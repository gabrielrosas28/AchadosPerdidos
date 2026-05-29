package com.escola.achadosperdidos.data.network

import android.content.Context
import android.util.Base64
import android.util.Log
import com.escola.achadosperdidos.data.local.CategoriaDao
import com.escola.achadosperdidos.data.local.ItemDao
import com.escola.achadosperdidos.data.model.Categoria
import com.escola.achadosperdidos.data.model.Item
import com.escola.achadosperdidos.data.network.dto.CriarCategoriaDto
import com.escola.achadosperdidos.data.network.dto.StatusServidor
import com.escola.achadosperdidos.data.network.dto.SyncItemDto
import com.escola.achadosperdidos.ui.admin.FotoStorage
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Coordena a sincronização offline-first entre o banco Room local e a API REST.
 *
 * **Fluxo (bidirecional):**
 * 1. Envia ao servidor categorias criadas localmente que ainda não têm [idServidor].
 * 2. Envia ao servidor itens marcados como `sincronizado = false`.
 * 3. Baixa categorias novas/alteradas no servidor e insere/atualiza localmente.
 * 4. Baixa itens novos/alterados no servidor (inclusive os criados pelo site)
 *    e insere/atualiza localmente, baixando a foto quando houver.
 *
 * Chamado pelo [com.escola.achadosperdidos.data.worker.LimpezaFotoWorker]
 * (diariamente) ou manualmente pelo gestor.
 *
 * [appContext] é o contexto de aplicação — necessário para gravar no disco as
 * fotos baixadas do servidor (via [FotoStorage]).
 */
class SyncRepository(
    private val api: ApiService,
    private val categoriaDao: CategoriaDao,
    private val itemDao: ItemDao,
    private val appContext: Context
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

        /**
         * `tabletId` atribuido localmente a itens criados no SITE (que vem do
         * servidor sem tabletId). So preenche o campo NOT NULL do modelo local;
         * a identidade real desses itens e o `idServidor`.
         */
        private const val TABLET_ID_SERVIDOR = "servidor"
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
        val remotas = try {
            api.baixarCategorias()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao baixar categorias do servidor: ${e.message}")
            return
        }
        Log.d(TAG, "Recebidas ${remotas.size} categorias do servidor.")

        // Snapshot local para casar por nome (categorias seed/criadas offline que
        // ainda nao tem idServidor) — n eh pequeno (~dezenas), custo irrelevante.
        val locais = categoriaDao.obterTodas()

        remotas.forEach { dto ->
            // 1. Ja temos essa categoria do servidor? Atualiza nome/ativa se mudou.
            val porServidor = locais.firstOrNull { it.idServidor == dto.id }
                ?: categoriaDao.obterPorIdServidor(dto.id)
            if (porServidor != null) {
                if (porServidor.nome != dto.nome || porServidor.ativa != dto.ativa) {
                    categoriaDao.atualizar(porServidor.copy(nome = dto.nome, ativa = dto.ativa))
                    Log.d(TAG, "Categoria id servidor=${dto.id} atualizada ('${dto.nome}', ativa=${dto.ativa}).")
                }
                return@forEach
            }

            // 2. Existe local com o mesmo nome mas sem idServidor? Adota o id (merge).
            val porNome = locais.firstOrNull {
                it.idServidor == null && it.nome.trim().equals(dto.nome.trim(), ignoreCase = true)
            }
            if (porNome != null) {
                categoriaDao.marcarSincronizada(porNome.id, dto.id)
                if (porNome.ativa != dto.ativa) {
                    categoriaDao.atualizar(porNome.copy(idServidor = dto.id, ativa = dto.ativa))
                }
                Log.d(TAG, "Categoria '${dto.nome}' casada por nome → adotou id servidor=${dto.id}.")
                return@forEach
            }

            // 3. Nova categoria (criada no site / outro tablet) → insere.
            //    emoji fica null e a UI cai no fallback emojiParaCategoria(nome).
            categoriaDao.inserirSeNaoExiste(
                Categoria(nome = dto.nome, ativa = dto.ativa, idServidor = dto.id)
            )
            Log.d(TAG, "Nova categoria recebida do servidor: '${dto.nome}' (id servidor=${dto.id}).")
        }
    }

    // ── 4. Baixar itens do servidor (inclui os criados pelo site) ─────────────

    /**
     * Baixa todos os itens do servidor (`GET /api/itens`) e reconcilia com o
     * banco local, de forma idempotente:
     *
     *  - Item que ESTE tablet criou (casa por `idLocalTablet`): mantém, só
     *    preenche o `idServidor` que faltava e atualiza status/devolução.
     *  - Item já baixado antes (casa por `idServidor`): atualiza status/devolução.
     *  - Item NOVO (criado no site — `idLocalTablet`/`tabletId` nulos — ou por
     *    outro tablet): insere localmente, baixando a foto se houver `urlFoto`.
     *
     * Itens vindos do servidor entram já com `sincronizado = true` (não há nada
     * a re-enviar). Requer que [baixarCategorias] tenha rodado antes, para mapear
     * `categoriaId` do servidor → categoria local.
     */
    suspend fun baixarItens() {
        val remotos = try {
            api.listarItens()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao baixar itens do servidor: ${e.message}")
            return
        }
        Log.d(TAG, "Recebidos ${remotos.size} itens do servidor.")

        var inseridos = 0
        var atualizados = 0

        for (dto in remotos) {
            val statusLocal = StatusServidor.deOrdinal(dto.status)
            val devolucao = parseDataServidor(dto.dataDevolucao)

            // 1. Item criado por ESTE tablet (idLocalTablet bate).
            val porUuid = dto.idLocalTablet
                ?.takeIf { it.isNotBlank() }
                ?.let { itemDao.obterPorIdLocalTablet(it) }
            if (porUuid != null) {
                if (porUuid.idServidor != dto.id || porUuid.status != statusLocal ||
                    porUuid.dataDevolucao != devolucao
                ) {
                    itemDao.atualizar(
                        porUuid.copy(
                            idServidor    = dto.id,
                            status        = statusLocal,
                            dataDevolucao = devolucao ?: porUuid.dataDevolucao
                        )
                    )
                    atualizados++
                }
                continue
            }

            // 2. Item já baixado antes (idServidor bate).
            val porServidor = itemDao.obterPorIdServidor(dto.id)
            if (porServidor != null) {
                if (porServidor.status != statusLocal || porServidor.dataDevolucao != devolucao) {
                    itemDao.atualizar(
                        porServidor.copy(status = statusLocal, dataDevolucao = devolucao)
                    )
                    atualizados++
                }
                continue
            }

            // 3. Item NOVO vindo do servidor → inserir.
            val catLocal = categoriaDao.obterPorIdServidor(dto.categoriaId)
            if (catLocal == null) {
                Log.w(TAG, "Item servidor id=${dto.id} ignorado: categoria " +
                        "${dto.categoriaId} ('${dto.categoriaNome}') ainda nao existe local.")
                continue
            }

            val caminhoFoto = if (!dto.urlFoto.isNullOrBlank()) baixarFoto(dto.urlFoto) else null

            val novo = Item(
                descricao               = dto.descricao,
                localEncontrado         = dto.localEncontrado,
                categoriaId             = catLocal.id,
                status                  = statusLocal,
                dataCadastro            = parseDataServidor(dto.dataCadastro) ?: Date(),
                dataDevolucao           = devolucao,
                caminhoFoto             = caminhoFoto,
                nomeArquivoFotoServidor = dto.urlFoto?.substringAfterLast('/')?.takeIf { it.isNotBlank() },
                idLocalTablet           = dto.idLocalTablet?.takeIf { it.isNotBlank() }
                    ?: UUID.randomUUID().toString(),
                idServidor              = dto.id,
                sincronizado            = true,
                tabletId                = dto.tabletId?.takeIf { it.isNotBlank() } ?: TABLET_ID_SERVIDOR
            )
            try {
                itemDao.inserir(novo)
                inseridos++
                Log.d(TAG, "Item servidor id=${dto.id} ('${dto.descricao}') inserido localmente.")
            } catch (e: Exception) {
                // Conflito de unique (idLocalTablet) ou FK — loga e segue.
                Log.w(TAG, "Falha ao inserir item servidor id=${dto.id}: ${e.message}")
            }
        }

        Log.i(TAG, "Download de itens concluido — inseridos=$inseridos, atualizados=$atualizados.")
    }

    /**
     * Baixa a imagem da [url] absoluta do servidor e grava na pasta interna do
     * app. Retorna o caminho absoluto local, ou `null` se falhar (item entra sem
     * foto; uma proxima sync tenta de novo via [obterPorIdServidor] → atualizar).
     */
    private suspend fun baixarFoto(url: String): String? {
        return try {
            val corpo = api.baixarArquivo(url)
            val bytes = corpo.use { it.bytes() }
            FotoStorage.salvarBytes(appContext, bytes)?.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao baixar foto '$url': ${e.message}")
            null
        }
    }

    /**
     * Converte data ISO-8601 do servidor (.NET serializa UTC com fração de
     * segundo e 'Z', ex: `2026-05-27T20:11:00.7500427Z`) para [Date].
     * Tolerante: descarta fração de segundo e sufixo 'Z', interpretando como UTC.
     */
    private fun parseDataServidor(iso: String?): Date? {
        if (iso.isNullOrBlank()) return null
        val base = iso.trim().substringBefore('.').removeSuffix("Z")
        return try {
            FMT_ISO.parse(base)
        } catch (e: Exception) {
            Log.w(TAG, "Data invalida do servidor: '$iso' (${e.message})")
            null
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
        baixarItens()
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
