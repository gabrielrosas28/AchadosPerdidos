package com.escola.achadosperdidos.data.network

import android.util.Base64
import android.util.Log
import com.escola.achadosperdidos.data.local.CategoriaDao
import com.escola.achadosperdidos.data.local.ItemDao
import com.escola.achadosperdidos.data.model.Categoria
import com.escola.achadosperdidos.data.network.dto.CriarCategoriaDto
import com.escola.achadosperdidos.data.network.dto.StatusServidor
import com.escola.achadosperdidos.data.network.dto.SyncItemDto
import kotlinx.coroutines.delay
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

        /**
         * Itens enviados por request HTTP. Cada item carrega a foto em
         * base64 (~1MB por foto inflado ~33%). Kestrel default aceita ~30MB
         * por request; 5 itens por chunk = ~6.5MB, com folga.
         */
        private const val TAMANHO_LOTE_ITENS = 5

        /**
         * Quantas vezes [sincronizarItens] varre a fila de pendentes antes
         * de desistir. Cobre cenarios onde a Wi-Fi do tablet cai por alguns
         * segundos no meio do envio — as proximas passadas reenviam o que
         * ficou. Aborta cedo se uma passada inteira nao consegue enviar
         * nenhum item (sinal de queda persistente do servidor/rede).
         */
        private const val MAX_PASSADAS_SYNC = 5

        /** Espera entre passadas quando ainda ha pendentes (ms). */
        private const val DELAY_ENTRE_PASSADAS_MS = 3_000L

        /** Espera apos um lote que falhou por rede, antes do proximo (ms). */
        private const val DELAY_APOS_FALHA_DE_REDE_MS = 1_500L

        /** Formato ISO-8601 UTC para envio de datas ao servidor .NET. */
        private val FMT_ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    // ── 1. Enviar categorias novas para o servidor ────────────────────────────

    /**
     * Para cada categoria local sem [idServidor]:
     *  - Se ja existe no servidor uma categoria com o mesmo nome (seed ou
     *    criada por outro tablet), apenas adota o `idServidor` dela (merge).
     *  - Caso contrario, cria no servidor.
     *
     * Esse merge evita o loop infinito de HTTP 409 Conflict (unique constraint
     * por nome no servidor) e garante que as categorias do tablet acabem com
     * `idServidor` preenchido — pre-requisito pra `sincronizarItens` mapear
     * o `categoriaServidorId` corretamente.
     */
    suspend fun sincronizarCategorias() {
        val novas = categoriaDao.obterNaoSincronizadas()
        if (novas.isEmpty()) return

        Log.d(TAG, "Sincronizando ${novas.size} categorias pendentes...")

        val remotasPorNome: Map<String, Int> = try {
            api.baixarCategorias().associate { it.nome.trim().lowercase() to it.id }
        } catch (e: Exception) {
            Log.w(TAG, "Nao consegui baixar categorias para merge: ${e.message}")
            emptyMap()
        }

        novas.forEach { cat ->
            val idRemoto = remotasPorNome[cat.nome.trim().lowercase()]
            if (idRemoto != null) {
                categoriaDao.marcarSincronizada(cat.id, idRemoto)
                Log.d(TAG, "Categoria '${cat.nome}' ja existia no servidor → adotada id=$idRemoto")
                return@forEach
            }
            try {
                val resposta = api.criarCategoria(CriarCategoriaDto(cat.nome, cat.idLocalTablet))
                categoriaDao.marcarSincronizada(cat.id, resposta.id)
                Log.d(TAG, "Categoria '${cat.nome}' criada → id servidor=${resposta.id}")
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao criar categoria '${cat.nome}': ${e.message}")
            }
        }
    }

    // ── 2. Enviar itens pendentes para o servidor ─────────────────────────────

    /**
     * Faz ate [MAX_PASSADAS_SYNC] passadas sobre a fila de itens com
     * `sincronizado = false`, enviando em lotes de [TAMANHO_LOTE_ITENS].
     *
     * Por que multiplas passadas: a Wi-Fi do tablet pode oscilar — uma
     * passada manda 90% e os ultimos lotes caem por falha de rede. A proxima
     * passada pega so o que restou (consulta o DAO de novo, ja sem os que
     * subiram), e tenta tudo o que sobrou.
     *
     * Aborta cedo quando uma passada inteira nao consegue enviar nenhum
     * item — sinal de queda persistente do servidor; nao adianta insistir.
     */
    suspend fun sincronizarItens() {
        for (passada in 1..MAX_PASSADAS_SYNC) {
            val pendentes = itemDao.obterNaoSincronizados()
            if (pendentes.isEmpty()) {
                if (passada == 1) Log.d(TAG, "Nenhum item pendente de sincronizacao.")
                else Log.i(TAG, "Sync de itens completa apos $passada passada(s) — fila zerada.")
                return
            }

            Log.d(TAG, "Sincronizando ${pendentes.size} itens pendentes " +
                    "(passada $passada/$MAX_PASSADAS_SYNC)...")

            val resumo = enviarLotesPendentes(pendentes)
            Log.i(TAG, "Passada $passada — criados=${resumo.criados}, " +
                    "atualizados=${resumo.atualizados}, " +
                    "lotes com falha=${resumo.lotesComFalha}/${resumo.totalLotes}")

            if (resumo.criados == 0 && resumo.atualizados == 0) {
                Log.w(TAG, "Passada $passada nao enviou nenhum item — abortando retry.")
                return
            }

            val aindaPendentes = itemDao.obterNaoSincronizados().size
            if (aindaPendentes == 0) {
                Log.i(TAG, "Sync de itens completa em $passada passada(s) — todos enviados.")
                return
            }

            if (passada < MAX_PASSADAS_SYNC) {
                Log.d(TAG, "$aindaPendentes itens ainda pendentes — aguardando " +
                        "${DELAY_ENTRE_PASSADAS_MS}ms antes da proxima passada.")
                delay(DELAY_ENTRE_PASSADAS_MS)
            }
        }
        val final = itemDao.obterNaoSincronizados().size
        if (final > 0) {
            Log.w(TAG, "Sync de itens terminou com $final itens ainda pendentes " +
                    "apos $MAX_PASSADAS_SYNC passadas.")
        }
    }

    private data class ResumoLotes(
        val criados: Int,
        val atualizados: Int,
        val lotesComFalha: Int,
        val totalLotes: Int,
    )

    /**
     * Uma passada: monta o payload de [pendentes], divide em chunks e envia
     * cada chunk. Itens cujo chunk teve sucesso vao pra `sincronizado = 1`.
     * Aplica um pequeno [DELAY_APOS_FALHA_DE_REDE_MS] depois de cada falha
     * de rede pra dar tempo da conexao se restabelecer antes do proximo chunk.
     */
    private suspend fun enviarLotesPendentes(
        pendentes: List<com.escola.achadosperdidos.data.model.Item>
    ): ResumoLotes {
        val enviar = mutableListOf<Pair<com.escola.achadosperdidos.data.model.Item, SyncItemDto>>()
        for (item in pendentes) {
            val cat = categoriaDao.obterPorId(item.categoriaId)
            if (cat == null) {
                Log.w(TAG, "Item id=${item.id} ignorado: categoria ${item.categoriaId} nao encontrada.")
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
        if (enviar.isEmpty()) return ResumoLotes(0, 0, 0, 0)

        val chunks = enviar.chunked(TAMANHO_LOTE_ITENS)
        var criados = 0
        var atualizados = 0
        var falhas = 0

        chunks.forEachIndexed { idx, chunk ->
            try {
                val resposta = api.sincronizarItens(chunk.map { it.second })
                Log.i(TAG, "Lote ${idx + 1}/${chunks.size} OK — criados=${resposta.criados}, " +
                        "atualizados=${resposta.atualizados}, erros=${resposta.erros.size}")
                criados += resposta.criados
                atualizados += resposta.atualizados

                if (resposta.erros.isNotEmpty()) {
                    Log.w(TAG, "Erros do servidor no lote ${idx + 1} " +
                            "(itens deste lote ficam pendentes): " +
                            resposta.erros.joinToString())
                    falhas++
                    return@forEachIndexed
                }

                chunk.forEach { (item, _) ->
                    itemDao.marcarSincronizado(
                        id                      = item.id,
                        idServidor              = null,
                        nomeArquivoFotoServidor = item.nomeArquivoFotoServidor
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao enviar lote ${idx + 1}/${chunks.size}: ${e.message}")
                falhas++
                // Pequena pausa antes do proximo lote — da tempo da Wi-Fi
                // restabelecer e evita derrubar todos os lotes em cascata.
                delay(DELAY_APOS_FALHA_DE_REDE_MS)
            }
        }

        return ResumoLotes(criados, atualizados, falhas, chunks.size)
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
