package com.escola.achadosperdidos.ui.admin

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.escola.achadosperdidos.AchadosPerdidosApp
import com.escola.achadosperdidos.data.backup.BackupExporter
import kotlinx.coroutines.launch

/**
 * Tela "Exportar backup" do Painel do Gestor.
 *
 * Fluxo: usuário toca em "Gerar backup" → SAF abre seletor de pasta com nome
 * já sugerido (`achadosperdidos-backup-AAAAMMDD-HHmm.zip`) → escreve um ZIP
 * contendo `dados.json` + `fotos/`. Em seguida oferece compartilhar via Intent
 * (Drive, Email, USB) para tirar o arquivo do tablet.
 */
@Composable
fun ExportarBackup() {
    val context = LocalContext.current
    val app = context.applicationContext as AchadosPerdidosApp
    val scope = rememberCoroutineScope()

    var estado by remember { mutableStateOf<EstadoExport>(EstadoExport.Ocioso) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri == null) {
            // Usuário cancelou — volta ao estado ocioso silenciosamente.
            estado = EstadoExport.Ocioso
            return@rememberLauncherForActivityResult
        }
        estado = EstadoExport.Gerando
        scope.launch {
            estado = runCatching {
                val resultado = context.contentResolver.openOutputStream(uri)?.use { out ->
                    BackupExporter.exportarPara(
                        app = app,
                        destino = out,
                        appVersionName = app.packageManager
                            .getPackageInfo(app.packageName, 0).versionName ?: "?",
                    )
                } ?: error("Não foi possível abrir o arquivo de destino.")
                EstadoExport.Sucesso(uri, resultado)
            }.getOrElse { erro ->
                EstadoExport.Falha(erro.message ?: erro.javaClass.simpleName)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "💾 Backup dos dados do tablet",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Gera um arquivo ZIP com todas as categorias, itens e fotos " +
                            "armazenados neste tablet. Use antes de mudar de servidor " +
                            "ou de reinstalar o app.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        when (val s = estado) {
            EstadoExport.Ocioso -> {
                Button(
                    onClick = { launcher.launch(BackupExporter.nomeArquivoSugerido()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "Gerar backup",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            EstadoExport.Gerando -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "Gerando backup… isso pode demorar alguns segundos " +
                                    "se houver muitas fotos.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            is EstadoExport.Sucesso -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "✅ Backup gerado",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Categorias: ${s.resultado.categorias}\n" +
                                    "Itens: ${s.resultado.itens}\n" +
                                    "Fotos incluídas: ${s.resultado.fotosIncluidas}" +
                                    if (s.resultado.fotosAusentes > 0) {
                                        "\nFotos ausentes (não encontradas no disco): " +
                                                "${s.resultado.fotosAusentes}"
                                    } else "",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = { compartilharZip(context, s.uri) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Compartilhar arquivo", style = MaterialTheme.typography.titleMedium)
                }

                OutlinedButton(
                    onClick = { estado = EstadoExport.Ocioso },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Gerar outro backup", style = MaterialTheme.typography.titleMedium)
                }
            }

            is EstadoExport.Falha -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "❌ Falha ao gerar backup",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            s.mensagem,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Button(
                    onClick = { estado = EstadoExport.Ocioso },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Tentar novamente", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private sealed interface EstadoExport {
    data object Ocioso : EstadoExport
    data object Gerando : EstadoExport
    data class Sucesso(val uri: Uri, val resultado: BackupExporter.Resultado) : EstadoExport
    data class Falha(val mensagem: String) : EstadoExport
}

private fun compartilharZip(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, "Compartilhar backup").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}
