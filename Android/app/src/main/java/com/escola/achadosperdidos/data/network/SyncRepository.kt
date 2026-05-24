package com.escola.achadosperdidos.data.network

import android.util.Base64
import android.util.Log
import com.escola.achadosperdidos.data.local.CategoriaDao
import com.escola.achadosperdidos.data.local.ItemDao
import com.escola.achadosperdidos.data.model.Categoria
import com.escola.achadosperdidos.data.network.dto.CriarCategoriaDto
import com.escola.achadosperdidos.data.network.dto.StatusServidor
import com.escola.achadosperdidos.data.network.dto.SyncItemDto
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Coordena a sincronização offline-first entre o banco Room local e a API REST.
 *
 * **Fluxo:**
 * 1. Envia ao servidor categorias criadas localmente que ainda não têm [idServidor].
 * 2. Envia ao servidor itens marcados como `sincronizado = false`.
 * 3. Baixa categorias novas/alteradas no servidor e insere localmente.
 *
 * Chamado pelo [com.escola.achadosperdidos.data.worker.LimpezaFotoWorker]
 * (diariamente) ou manualmente pelo gestor.
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
     * Cria no servidor cada categoria sem [idServidor].
     * Erros individuais não interrompem o lote.
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
     * Coleta itens com `sincronizado = false`, monta o payload e envia em lote.
     * A foto local é lida do disco e codificada em Base64.
     *
     * Só marca os itens como sincronizados se o servidor retornar sem erros —
     * caso contrário, deixa para tentar novamente no próximo ciclo do Worker.
     */
    suspend fun sincronizarItens() {
        val pendentes = itemDao.obterNaoSincronizados()
        if (pendentes.isEmpty()) {
            Log.d(TAG, "Nenhum item pendente de sincronização.")
            return
        }

        Log.d(TAG, "Sincronizando ${pendentes.size} itens pendentes...")

        // Monta o lote, pulando itens cuja categoria ainda não existe (raro).
        val enviar = mutableListOf<Pair<com.escola.achadosperdidos.data.model.Item, SyncItemDto>>()
        for (item in pendentes) {
            val cat = categoriaDao.obterPorId(item.categoriaId)
            if (cat == null) {
                Log.w(TAG, "Item id=${item.id} ignorado: categoria ${item.categoriaId} não encontrada.")
                continue
            }
            val (fotoBase64, nomeFoto) = lerFotoComoBase64(item.caminhoFoto)

            enviar += item to SyncItemDto(
                descricao              = item.descricao,
                localEncontrado        = item.localEncontrado,
                categoriaServidorId    = cat.idServidor,
                categoriaIdLocalTablet = cat.idLocalTablet,
                status                 = StatusServidor.paraOrdinal(item.status),
                dataCadastro           = FMT_ISO.format(item.dataCadastro),
                dataDevolucao          = item.dataDevolucao?.let { FMT_ISO.format(it) },
                tabletId               = item.tabletId,
                idLocalTablet          = item.idLocalTablet,
                fotoBase64             = fotoBase64,
                nomeArquivoFoto        = nomeFoto
            )
        }
        if (enviar.isEmpty()) return

        try {
            val resposta = api.sincronizarItens(enviar.map { it.second })
            Log.i(TAG, "Sync OK — criados=${resposta.criados}, " +
                    "atualizados=${resposta.atualizados}, erros=${resposta.erros.size}")

            if (resposta.erros.isNotEmpty()) {
                // Política conservadora: se houve QUALQUER erro, não marca nada como sincronizado;
                // o batch atual da API não retorna correspondência item→erro, então não dá pra
                // saber quais especificamente falharam. Próximo ciclo do Worker tenta de novo.
                Log.w(TAG, "Erros do servidor (mantendo itens pendentes): " +
                        resposta.erros.joinToString())
                return
            }

            // Sucesso total — marca todos como sincronizados.
            // idServidor fica null porque o batch não retorna IDs individuais.
            enviar.forEach { (item, _) ->
                itemDao.marcarSincronizado(
                    id                      = item.id,
                    idServidor              = null,
                    nomeArquivoFotoServidor = item.nomeArquivoFotoServidor
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao enviar lote de itens: ${e.message}")
            // Não relança — WorkManager retentará no próximo ciclo.
        }
    }

    // ── 3. Baixar categorias do servidor ─────────────────────────────────────

    /**
     * Baixa categorias do servidor e insere localmente as ausentes.
     * Usa [CategoriaDao.obterPorIdServidor] para idempotência (não cria duplicatas).
     */
    suspend fun baixarCategorias() {
        try {
            val remotas = api.baixarCategorias()
            Log.d(TAG, "Recebidas ${remotas.size} categorias do servidor.")

            remotas.forEach { dto ->
                val jaExiste = categoriaDao.obterPorIdServidor(dto.id) != null
                if (!jaExiste) {
                    categoriaDao.inserirSeNaoExiste(
                        Categoria(
                            nome       = dto.nome,
                            ativa      = dto.ativa,
                            idServidor = dto.id
                        )
                    )
                    Log.d(TAG, "Nova categoria recebida: '${dto.nome}' (id servidor=${dto.id})")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao baixar categorias do servidor: ${e.message}")
        }
    }

    // ── Ponto de entrada único ────────────────────────────────────────────────

    /**
     * Executa o ciclo completo na ordem correta — categorias antes dos itens,
     * pois itens precisam que suas categorias já estejam mapeadas no servidor.
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
