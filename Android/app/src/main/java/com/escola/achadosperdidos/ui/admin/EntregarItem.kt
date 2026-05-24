package com.escola.achadosperdidos.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.escola.achadosperdidos.data.local.ItemComCategoria
import com.escola.achadosperdidos.data.model.StatusItem
import com.escola.achadosperdidos.viewmodel.AchadosViewModel

/**
 * Listagem dos itens com status **Encontrado** — gestor toca em um para dar baixa
 * (status → Devolvido). Pop-up de confirmação evita baixas acidentais.
 */
@Composable
fun EntregarItem(
    achadosVM: AchadosViewModel,
    modifier: Modifier = Modifier
) {
    // Garante o filtro correto ao entrar e restaura ao sair
    LaunchedEffect(Unit) {
        achadosVM.selecionarCategoria(null)
        achadosVM.setStatusFiltro(StatusItem.Encontrado)
    }

    val itens by achadosVM.itens.collectAsState()
    var paraConfirmar by remember { mutableStateOf<ItemComCategoria?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "✔️ Entregar Item",
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
                        text = "Nenhum item disponível para entrega.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(200.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(itens, key = { it.item.id }) { ic ->
                CardItemEntrega(ic, onClick = { paraConfirmar = ic })
            }
        }
    }

    // ── Dialog de confirmação ────────────────────────────────────────────────
    paraConfirmar?.let { ic ->
        AlertDialog(
            onDismissRequest = { paraConfirmar = null },
            icon = { Text("✔️", style = TextStyle(fontSize = 48.sp)) },
            title = {
                Text(
                    "Confirmar entrega?",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    "\"${ic.item.descricao}\"\n\nEsta ação marca o item como devolvido " +
                            "e remove a foto do tablet (texto fica no histórico).",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    achadosVM.marcarComoDevolvido(ic.item.id)
                    paraConfirmar = null
                }) {
                    Text("Confirmar entrega", style = MaterialTheme.typography.titleMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { paraConfirmar = null }) {
                    Text("Cancelar", style = MaterialTheme.typography.titleMedium)
                }
            }
        )
    }
}

@Composable
private fun CardItemEntrega(ic: ItemComCategoria, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.aspectRatio(0.78f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                if (ic.item.caminhoFoto != null) {
                    AsyncImage(
                        model = ic.item.caminhoFoto,
                        contentDescription = ic.item.descricao,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("📷", style = TextStyle(fontSize = 48.sp))
                    }
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    text = ic.item.descricao,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Text(
                    text = ic.categoriaNome,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
