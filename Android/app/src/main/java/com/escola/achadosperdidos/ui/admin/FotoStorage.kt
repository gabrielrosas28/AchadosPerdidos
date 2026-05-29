package com.escola.achadosperdidos.ui.admin

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Gerencia a pasta interna onde as fotos dos itens são armazenadas no tablet:
 * `Context.filesDir/fotos_itens/` — protegida (somente o app acessa) e o caminho
 * exposto à câmera via [FileProvider] (autoridade `${applicationId}.fileprovider`).
 *
 * A pasta tem expurgo periódico no [com.escola.achadosperdidos.data.worker.LimpezaFotoWorker]:
 * fotos de itens **Devolvidos** ou com **+180 dias** são apagadas.
 */
object FotoStorage {

    private const val PASTA = "fotos_itens"

    /** Retorna (e cria, se faltar) a pasta de fotos no armazenamento interno. */
    fun pasta(context: Context): File =
        File(context.filesDir, PASTA).apply { mkdirs() }

    /**
     * Cria um par (arquivo, Uri) para uso com o intent da câmera.
     *  - O arquivo fica em `filesDir/fotos_itens/{uuid}.jpg`.
     *  - O Uri é gerado pelo FileProvider e pode ser passado para
     *    [androidx.activity.result.contract.ActivityResultContracts.TakePicture].
     *
     * Após a câmera retornar `true`, o arquivo terá a foto capturada — basta
     * gravar [File.absolutePath] no campo `caminhoFoto` do Item.
     */
    fun criarArquivoTempFoto(context: Context): Pair<File, Uri> {
        val arquivo = File(pasta(context), "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            arquivo
        )
        return arquivo to uri
    }

    /**
     * Copia uma foto escolhida na galeria para a pasta gerenciada do app.
     *
     * Necessário porque:
     *  - O campo [com.escola.achadosperdidos.data.model.Item.caminhoFoto] guarda um
     *    path absoluto local, não um content:// Uri (que pode expirar).
     *  - O [com.escola.achadosperdidos.data.worker.LimpezaFotoWorker] só conhece
     *    a pasta `filesDir/fotos_itens/` para o expurgo automático.
     *
     * Retorna `null` se não conseguir abrir o stream da Uri (ex: usuário cancelou,
     * provider revogou a permissão). O arquivo destino é apagado em caso de erro.
     */
    fun copiarDaGaleria(context: Context, origem: Uri): File? {
        val destino = File(pasta(context), "${UUID.randomUUID()}.jpg")
        return runCatching {
            context.contentResolver.openInputStream(origem)?.use { input ->
                destino.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            destino
        }.getOrElse {
            destino.delete()
            null
        }
    }

    /**
     * Grava os bytes de uma foto baixada do servidor na pasta gerenciada do app.
     * Usado pelo download de itens (servidor → tablet) em
     * [com.escola.achadosperdidos.data.network.SyncRepository.baixarItens].
     *
     * Retorna o [File] gravado, ou `null` se a escrita falhar (o arquivo
     * parcial é apagado nesse caso).
     */
    fun salvarBytes(context: Context, bytes: ByteArray): File? {
        val destino = File(pasta(context), "${UUID.randomUUID()}.jpg")
        return runCatching {
            destino.outputStream().use { it.write(bytes) }
            destino
        }.getOrElse {
            destino.delete()
            null
        }
    }
}
