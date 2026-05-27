package com.escola.achadosperdidos.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.escola.achadosperdidos.viewmodel.AchadosViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Tela "Sincronizar Agora" do Painel do Gestor.
 *
 * Conta itens/categorias pendentes antes, dispara o ciclo completo via
 * [AchadosViewModel.sincronizarAgora] e mostra um resumo do que foi enviado.
 */
@Composable
fun SincronizarAgora(achadosVM: AchadosViewModel) {
    val context = LocalContext.current
    val app = context.applicationContext as AchadosPerdidosApp
    val scope = rememberCoroutineScope()

    var estado by remember { mutableStateOf<EstadoSync>(EstadoSync.Ocioso) }

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
                    "🔄 Sincronizar com o servidor",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Envia ao servidor as categorias e itens novos cadastrados " +
                            "no tablet, e baixa categorias criadas em outros lugares. " +
                            "Pode demorar dependendo do número de fotos.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        when (val s = estado) {
            EstadoSync.Ocioso -> {
                Button(
                    onClick = {
                        scope.launch {
                            estado = EstadoSync.Contando
                            val antes = contarPendentes(app)
                            estado = EstadoSync.Sincronizando(antes)
                            val sucesso = suspendCancellableCoroutine<Boolean> { cont ->
                                achadosVM.sincronizarAgora { ok -> cont.resume(ok) }
                            }
                            val depois = contarPendentes(app)
                            estado = EstadoSync.Concluido(
                                sucesso = sucesso,
                                categoriasEnviadas = (antes.categoriasPendentes - depois.categoriasPendentes)
                                    .coerceAtLeast(0),
                                itensEnviados = (antes.itensPendentes - depois.itensPendentes)
                                    .coerceAtLeast(0),
                                categoriasAindaPendentes = depois.categoriasPendentes,
                                itensAindaPendentes = depois.itensPendentes,
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "Sincronizar agora",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            EstadoSync.Contando, is EstadoSync.Sincronizando -> {
                CardProgresso(
                    titulo = if (s is EstadoSync.Sincronizando)
                        "Sincronizando…"
                    else "Preparando…",
                    subtitulo = if (s is EstadoSync.Sincronizando) {
                        "Enviando ${s.antes.itensPendentes} itens e " +
                                "${s.antes.categoriasPendentes} categorias pendentes."
                    } else {
                        "Contando registros pendentes…"
                    }
                )
            }

            is EstadoSync.Concluido -> {
                val container = if (s.sucesso && s.itensAindaPendentes == 0)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
                Card(
                    colors = CardDefaults.cardColors(containerColor = container),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (s.sucesso) "✅ Sincronização concluída" else "⚠️ Sincronização com problemas",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Categorias enviadas agora: ${s.categoriasEnviadas}\n" +
                                    "Itens enviados agora: ${s.itensEnviados}\n" +
                                    "Ainda pendentes: ${s.itensAindaPendentes} itens, " +
                                    "${s.categoriasAindaPendentes} categorias",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (s.itensAindaPendentes > 0) {
                            Text(
                                "Itens pendentes podem ser por: erro de rede, servidor fora " +
                                        "do ar, ou erros validados pelo servidor (ver logcat).",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { estado = EstadoSync.Ocioso },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Sincronizar novamente", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun CardProgresso(titulo: String, subtitulo: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitulo, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private data class Pendentes(val categoriasPendentes: Int, val itensPendentes: Int)

private suspend fun contarPendentes(app: AchadosPerdidosApp): Pendentes = withContext(Dispatchers.IO) {
    val cats = app.database.categoriaDao().obterNaoSincronizadas().size
    val itens = app.database.itemDao().obterNaoSincronizados().size
    Pendentes(cats, itens)
}

private sealed interface EstadoSync {
    data object Ocioso : EstadoSync
    data object Contando : EstadoSync
    data class Sincronizando(val antes: Pendentes) : EstadoSync
    data class Concluido(
        val sucesso: Boolean,
        val categoriasEnviadas: Int,
        val itensEnviados: Int,
        val categoriasAindaPendentes: Int,
        val itensAindaPendentes: Int,
    ) : EstadoSync
}

