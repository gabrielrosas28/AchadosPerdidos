package com.escola.achadosperdidos.data.network

import android.util.Base64
import android.util.Log
import com.escola.achadosperdidos.data.local.CategoriaDao
import com.escola.achadosperdidos.data.local.ItemDao
import com.escola.achadosperdidos.data.model.Categoria
import com.escola.achadosperdidos.data.network.dto.CriarCategoriaDto
import com.escola.achadosperdidos.data.network.dto.SyncItemDto
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Coordena a sincronização offline-first entre o banco Room local e a API REST.
 *
 * **Fluxo:**
 * 1. Envia ao servidor categorias criadas localmente que ainda não têm [idServidor].
 * 2. Envia ao servidor itens marcados como [sincronizado = false].
 * 3. Baixa categorias novas/alteradas no servidor e insere localmente.
 *
 * Chamado pelo [LimpezaFotoWorker] (diariamente) ou manualmente pelo gestor.
 */
class SyncRepository(
    private val api: ApiService,
    private val categoriaDao: CategoriaDao,
    private val itemDao: ItemDao
) {

    companion object {
        private const val TAG = "SyncRepository"

        /** Formato ISO-8601 UTC para envio de datas ao servidor .NET. */
        private val FMT_ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    // ── 1. Enviar categorias novas para o servidor ────────────────────────────

    /**
     * Busca todas as categorias sem [idServidor] e as cria na API.
     * Em caso de erro individual, loga e continua com as demais.
     */
    suspend fun sincronizarCategorias() {
        val novas = categoriaDao.obterNaoSincronizadas()
        if (novas.isEmpty()) return

        Log.d(TAG, "Sincronizando ${novas.size} categorias pendentes...")
        novas.forEach { cat ->
            try {
                val resposta = api.criarCategoria(CriarCategoriaDto(cat.nome, cat.idLocalTablet))
                categoriaDao.marcarSincronizada(cat.id, resposta.id)
                Log.d(TAG, "Categoria '${cat.nome}' → id servidor=${resposta.id}")
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao sincronizar categoria '${cat.nome}': ${e.message}")
            }
        }
    }

    // ── 2. Enviar itens pendentes para o servidor ─────────────────────────────

    /**
     * Coleta itens com [sincronizado = false], monta o payload de sync e envia em lote.
     * A foto é lida do disco e encodada em Base64 (enviada junto no payload JSON).
     */
    suspend fun sincronizarItens() {
        val pendentes = itemDao.obterNaoSincronizados()
        if (pendentes.isEmpty()) {
            Log.d(TAG, "Nenhum item pendente de sincronização.")
            return
        }

        Log.d(TAG, "Sincronizando ${pendentes.size} itens pendentes...")

        val lote = pendentes.mapNotNull { item ->
            val cat = categoriaDao.obterPorId(item.categoriaId)
            if (cat == null) {
                Log.w(TAG, "Item id=${item.id} ignorado: categoria ${item.categoriaId} não encontrada.")
                return@mapNotNull null
            }

            // Lê a foto local e codifica em Base64 (somente se o arquivo ainda existir)
            val (fotoBase64, nomeFoto) = lerFotoComoBase64(item.caminhoFoto)

            SyncItemDto(
                descricao              = item.descricao,
                localEncontrado       = item.localEncontrado,
                categoriaServidorId   = cat.idServidor,
                categoriaIdLocalTablet = cat.idLocalTablet,
                status                = item.status.name,
                dataCadastro          = FMT_ISO.format(item.dataCadastro),
                dataDevolucao         = item.dataDevolucao?.let { FMT_ISO.format(it) },
                tabletId              = item.tabletId,
                idLocalTablet         = item.idLocalTablet,
                fotoBase64            = fotoBase64,
                nomeArquivoFoto       = nomeFoto
            )
        }

        if (lote.isEmpty()) return

        try {
            val resposta = api.sincronizarItens(lote)
            Log.i(TAG, "Sync OK — criados=${resposta.criados}, " +
                    "atualizados=${resposta.atualizados}, erros=${resposta.erros.size}")

            if (resposta.erros.isNotEmpty()) {
                Log.w(TAG, "Erros do servidor: ${resposta.erros.joinToString()}")
            }

            // Marca localmente como sincronizados.
            // Obs.: a rota de batch atual não retorna IDs individuais;
            // quando o servidor suportar, atualize aqui para persistir idServidor correto.
            pendentes.forEach { item ->
                itemDao.marcarSincronizado(
                    id                    = item.id,
                    idServidor            = item.idServidor ?: 0,
                    nomeArquivoFotoServidor = item.nomeArquivoFotoServidor
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao enviar lote de itens: ${e.message}")
            // Não relança — o WorkManager vai retry automaticamente
        }
    }

    // ── 3. Baixar categorias do servidor ─────────────────────────────────────

    /**
     * Baixa as categorias do servidor e insere as que ainda não existem localmente.
     * Usa [CategoriaDao.inserirSeNaoExiste] para garantir idempotência.
     */
    suspend fun baixarCategorias() {
        try {
            val remotas = api.baixarCategorias()
            Log.d(TAG, "Recebidas ${remotas.size} categorias do servidor.")

            remotas.forEach { dto ->
                // Verifica se já existe pelo idServidor para não duplicar
                val existente = categoriaDao.obterPorId(dto.id.toLong())
                if (existente == null) {
                    categoriaDao.inserirSeNaoExiste(
                        Categoria(
                            nome       = dto.nome,
                            ativa      = dto.ativa,
                            idServidor = dto.id
                        )
                    )
                    Log.d(TAG, "Nova categoria recebida: '${dto.nome}' (id=${dto.id})")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao baixar categorias do servidor: ${e.message}")
        }
    }

    // ── Ponto de entrada único ────────────────────────────────────────────────

    /**
     * Executa todo o ciclo de sincronização na ordem correta:
     * categorias primeiro (itens dependem dos IDs das categorias).
     */
    suspend fun sincronizarTudo() {
        sincronizarCategorias()
        sincronizarItens()
        baixarCategorias()
    }

    // ── Utilitários privados ─────────────────────────────────────────────────

    /**
     * Lê um arquivo de imagem do disco e retorna (base64, nomeArquivo).
     * Retorna (null, null) se o caminho for nulo ou o arquivo não existir.
     */
    private fun lerFotoComoBase64(caminho: String?): Pair<String?, String?> {
        if (caminho == null) return null to null
        val arquivo = File(caminho)
        if (!arquivo.exists()) return null to null

        return try {
            val bytes = arquivo.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP) to arquivo.name
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível ler a foto '$caminho': ${e.message}")
            null to null
        }
    }
}
