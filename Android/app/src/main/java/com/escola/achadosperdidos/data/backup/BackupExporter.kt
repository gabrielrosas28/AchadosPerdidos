package com.escola.achadosperdidos.data.backup

import com.escola.achadosperdidos.AchadosPerdidosApp
import com.escola.achadosperdidos.data.model.Categoria
import com.escola.achadosperdidos.data.model.Item
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Gera um ZIP de backup completo dos dados locais do tablet:
 *
 *   backup.zip
 *   ├── dados.json    (categorias + itens + metadados)
 *   └── fotos/        (uma entrada por item.caminhoFoto existente em disco)
 *
 * Pensado para rodar 1× antes da primeira sincronização com o servidor — quando
 * o tablet acumulou meses de cadastros locais e queremos preservar tudo num
 * arquivo portátil (Drive, Email, pendrive) antes de integrar.
 */
object BackupExporter {

    private const val VERSAO_FORMATO = 1
    private const val PASTA_FOTOS_NO_ZIP = "fotos"

    /**
     * Escreve o ZIP de backup direto no [destino] (tipicamente um
     * `OutputStream` obtido de `contentResolver.openOutputStream(uri)` após
     * o usuário escolher onde salvar via SAF). Não fecha o destino — quem
     * abriu deve fechar.
     */
    suspend fun exportarPara(
        app: AchadosPerdidosApp,
        destino: OutputStream,
        appVersionName: String,
    ): Resultado = withContext(Dispatchers.IO) {
        val db = app.database
        val tabletId = app.tabletId
        val categorias = db.categoriaDao().obterTodas()
        val itens = db.itemDao().obterTodos()

        val mapeamentoFotos = construirMapeamentoFotos(itens)

        val payload = PayloadBackup(
            versao = VERSAO_FORMATO,
            exportadoEm = isoUtc(Date()),
            appVersionName = appVersionName,
            tabletId = tabletId,
            totais = Totais(
                categorias = categorias.size,
                itens = itens.size,
                fotosIncluidas = mapeamentoFotos.values.count { it != null },
                fotosAusentes = mapeamentoFotos.values.count { it == null },
            ),
            categorias = categorias.map { it.paraDto() },
            itens = itens.map { item ->
                val nomeNoZip = mapeamentoFotos[item.id]
                item.paraDto(caminhoFotoNoZip = nomeNoZip?.let { "$PASTA_FOTOS_NO_ZIP/$it" })
            },
        )

        val gson = GsonBuilder().setPrettyPrinting().create()
        val jsonBytes = gson.toJson(payload).toByteArray(Charsets.UTF_8)

        ZipOutputStream(destino.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("dados.json"))
            zip.write(jsonBytes)
            zip.closeEntry()

            for ((itemId, nomeNoZip) in mapeamentoFotos) {
                if (nomeNoZip == null) continue
                val item = itens.firstOrNull { it.id == itemId } ?: continue
                val origem = item.caminhoFoto?.let { File(it) } ?: continue
                if (!origem.exists()) continue

                zip.putNextEntry(ZipEntry("$PASTA_FOTOS_NO_ZIP/$nomeNoZip"))
                origem.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }

            zip.finish()
        }

        Resultado(
            categorias = payload.totais.categorias,
            itens = payload.totais.itens,
            fotosIncluidas = payload.totais.fotosIncluidas,
            fotosAusentes = payload.totais.fotosAusentes,
        )
    }

    /**
     * Nome de arquivo sugerido para o ZIP — passe ao
     * `CreateDocument("application/zip")` na UI.
     */
    fun nomeArquivoSugerido(agora: Date = Date()): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(agora)
        return "achadosperdidos-backup-$ts.zip"
    }

    data class Resultado(
        val categorias: Int,
        val itens: Int,
        val fotosIncluidas: Int,
        val fotosAusentes: Int,
    )

    /**
     * Mapeia itemId → nome único do arquivo dentro de `fotos/` no ZIP, ou
     * `null` se o item não tem foto ou se o arquivo não existe em disco.
     * O nome no ZIP preserva o basename original (que já é UUID).
     */
    private fun construirMapeamentoFotos(itens: List<Item>): Map<Long, String?> {
        val usados = mutableSetOf<String>()
        return itens.associate { item ->
            val caminho = item.caminhoFoto
            if (caminho.isNullOrBlank()) {
                item.id to null
            } else {
                val arquivo = File(caminho)
                if (!arquivo.exists()) {
                    item.id to null
                } else {
                    var nome = arquivo.name
                    var contador = 1
                    while (!usados.add(nome)) {
                        val base = arquivo.nameWithoutExtension
                        val ext = arquivo.extension
                        nome = if (ext.isNotEmpty()) "$base-$contador.$ext" else "$base-$contador"
                        contador++
                    }
                    item.id to nome
                }
            }
        }
    }

    private fun Categoria.paraDto() = CategoriaDto(
        id = id,
        nome = nome,
        emoji = emoji,
        ativa = ativa,
        dataCriacao = isoUtc(dataCriacao),
        idLocalTablet = idLocalTablet,
        idServidor = idServidor,
    )

    private fun Item.paraDto(caminhoFotoNoZip: String?) = ItemDto(
        id = id,
        descricao = descricao,
        localEncontrado = localEncontrado,
        categoriaId = categoriaId,
        status = status.name,
        dataCadastro = isoUtc(dataCadastro),
        dataDevolucao = dataDevolucao?.let { isoUtc(it) },
        caminhoFotoOriginal = caminhoFoto,
        caminhoFotoNoZip = caminhoFotoNoZip,
        nomeArquivoFotoServidor = nomeArquivoFotoServidor,
        idLocalTablet = idLocalTablet,
        idServidor = idServidor,
        sincronizado = sincronizado,
        tabletId = tabletId,
    )

    private fun isoUtc(d: Date): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(d)
    }

    // ── DTOs (formato estável do JSON; não usar entidades Room diretamente) ──

    private data class PayloadBackup(
        val versao: Int,
        val exportadoEm: String,
        val appVersionName: String,
        val tabletId: String,
        val totais: Totais,
        val categorias: List<CategoriaDto>,
        val itens: List<ItemDto>,
    )

    private data class Totais(
        val categorias: Int,
        val itens: Int,
        val fotosIncluidas: Int,
        val fotosAusentes: Int,
    )

    private data class CategoriaDto(
        val id: Long,
        val nome: String,
        val emoji: String?,
        val ativa: Boolean,
        val dataCriacao: String,
        val idLocalTablet: String,
        val idServidor: Int?,
    )

    private data class ItemDto(
        val id: Long,
        val descricao: String,
        val localEncontrado: String?,
        val categoriaId: Long,
        val status: String,
        val dataCadastro: String,
        val dataDevolucao: String?,
        val caminhoFotoOriginal: String?,
        val caminhoFotoNoZip: String?,
        val nomeArquivoFotoServidor: String?,
        val idLocalTablet: String,
        val idServidor: Int?,
        val sincronizado: Boolean,
        val tabletId: String,
    )
}
