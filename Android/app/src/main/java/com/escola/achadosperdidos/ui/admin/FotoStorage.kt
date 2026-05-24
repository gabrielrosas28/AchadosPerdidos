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
}
