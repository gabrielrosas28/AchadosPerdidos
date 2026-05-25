package com.escola.achadosperdidos.ui.publico

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.escola.achadosperdidos.R
import com.escola.achadosperdidos.data.local.ItemComCategoria
import com.escola.achadosperdidos.data.model.Categoria

// ════════════════════════════════════════════════════════════════════════════
//  Grade de categorias (tela inicial do Modo Público)
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun GradeCategorias(
    categorias: List<Categoria>,
    onClick: (Categoria) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.titulo_grade),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp)
        )

        LazyVerticalGrid(
            // Adaptativo: em tablet portrait ~800dp dá 2-3 cols; landscape dá mais.
            columns = GridCells.Adaptive(minSize = 200.dp),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(categorias, key = { it.id }) { cat ->
                CardCategoria(cat, onClick = { onClick(cat) })
            }
        }
    }
}

@Composable
private fun CardCategoria(cat: Categoria, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.aspectRatio(1f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = emojiDe(cat),
                style = TextStyle(fontSize = 80.sp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = cat.nome,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Grade de itens (após clicar numa categoria)
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun GradeItens(
    itens: List<ItemComCategoria>,
    onClick: (ItemComCategoria) -> Unit,
    modifier: Modifier = Modifier
) {
    if (itens.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔍", style = TextStyle(fontSize = 96.sp))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.sem_itens),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(220.dp),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(itens, key = { it.item.id }) { it ->
            CardItem(it, onClick = { onClick(it) })
        }
    }
}

@Composable
private fun CardItem(itemCom: ItemComCategoria, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.aspectRatio(0.78f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            // Área da foto — ocupa a maior parte do card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                if (itemCom.item.caminhoFoto != null) {
                    AsyncImage(
                        model = itemCom.item.caminhoFoto,
                        contentDescription = itemCom.item.descricao,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("📷", style = TextStyle(fontSize = 56.sp))
                    }
                }
            }
            // Descrição
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = itemCom.item.descricao,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                if (itemCom.item.localEncontrado != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = itemCom.item.localEncontrado,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
