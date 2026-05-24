package com.escola.achadosperdidos.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escola.achadosperdidos.data.model.StatusItem
import com.escola.achadosperdidos.viewmodel.AchadosViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Listagem de **todos os itens já devolvidos** — somente leitura.
 *
 * As fotos locais costumam ter sido apagadas pelo
 * [com.escola.achadosperdidos.data.worker.LimpezaFotoWorker], então o texto
 * (descrição, local, data de devolução) é a fonte da informação.
 */
@Composable
fun Historico(
    achadosVM: AchadosViewModel,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        achadosVM.selecionarCategoria(null)
        achadosVM.setStatusFiltro(StatusItem.Devolvido)
    }

    val itens by achadosVM.itens.collectAsState()
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "📜 Histórico de Devoluções",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(20.dp)
        )

        if (itens.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", style = TextStyle(fontSize = 96.sp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Nenhum item devolvido ainda.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(itens, key = { it.item.id }) { ic ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✔️",
                            style = TextStyle(fontSize = 36.sp),
                            modifier = Modifier
                                .size(48.dp)
                                .padding(end = 4.dp)
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                text = ic.item.descricao,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = ic.categoriaNome,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ic.item.dataDevolucao?.let { data ->
                                Text(
                                    text = "Devolvido em ${fmt.format(data)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
