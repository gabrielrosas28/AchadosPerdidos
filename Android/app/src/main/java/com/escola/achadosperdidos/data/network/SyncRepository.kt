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

class SyncRepository(
    private val api: ApiService,
    private val categoriaDao: CategoriaDao,
    private val itemDao: ItemDao,
    private val appContext: Context
) {

    companion object {
        private const val TAG = "SyncRepository"

        private const val TAMANHO_LOTE_ITENS = 5

        private const val MAX_PASSADAS_SYNC = 5

        private const val DELAY_ENTRE_PASSADAS_MS = 3_000L

        private const val DELAY_APOS_FALHA_DE_REDE_MS = 1_500L

        private val FMT_ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        private const val TABLET_ID_SERVIDOR = "servidor"
    }

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
                delay(DELAY_APOS_FALHA_DE_REDE_MS)
            }
        }

        return ResumoLotes(criados, atualizados, falhas, chunks.size)
    }

    suspend fun baixarCategorias() {
        val remotas = try {
            api.baixarCategorias()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao baixar categorias do servidor: ${e.message}")
            return
        }
        Log.d(TAG, "Recebidas ${remotas.size} categorias do servidor.")

        val locais = categoriaDao.obterTodas()

        remotas.forEach { dto ->
            val porServidor = locais.firstOrNull { it.idServidor == dto.id }
                ?: categoriaDao.obterPorIdServidor(dto.id)
            if (porServidor != null) {
                if (porServidor.nome != dto.nome || porServidor.ativa != dto.ativa) {
                    categoriaDao.atualizar(porServidor.copy(nome = dto.nome, ativa = dto.ativa))
                    Log.d(TAG, "Categoria id servidor=${dto.id} atualizada ('${dto.nome}', ativa=${dto.ativa}).")
                }
                return@forEach
            }

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

            categoriaDao.inserirSeNaoExiste(
                Categoria(nome = dto.nome, ativa = dto.ativa, idServidor = dto.id)
            )
            Log.d(TAG, "Nova categoria recebida do servidor: '${dto.nome}' (id servidor=${dto.id}).")
        }
    }

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
                Log.w(TAG, "Falha ao inserir item servidor id=${dto.id}: ${e.message}")
            }
        }

        Log.i(TAG, "Download de itens concluido — inseridos=$inseridos, atualizados=$atualizados.")
    }

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

    suspend fun sincronizarTudo() {
        sincronizarCategorias()
        sincronizarItens()
        baixarCategorias()
        baixarItens()
    }

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
