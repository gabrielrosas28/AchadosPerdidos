package com.escola.achadosperdidos.ui.admin

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object FotoStorage {

    private const val PASTA = "fotos_itens"

    fun pasta(context: Context): File =
        File(context.filesDir, PASTA).apply { mkdirs() }

    fun criarArquivoTempFoto(context: Context): Pair<File, Uri> {
        val arquivo = File(pasta(context), "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            arquivo
        )
        return arquivo to uri
    }

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
