package com.escola.achadosperdidos.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.escola.achadosperdidos.ui.publico.emojiParaCategoria
import com.escola.achadosperdidos.viewmodel.AchadosViewModel

/**
 * Tela do gestor para criar uma nova categoria.
 *
 * - Campo de nome (validação: não vazio, não duplicado)
 * - Lista das categorias existentes (para referência visual)
 * - Mostra emoji previsto pelo helper [emojiParaCategoria] enquanto digita
 */
@Composable
fun NovaCategoria(
    achadosVM: AchadosViewModel,
    onVoltar: () -> Unit,
    onCategoriaSalva: () -> Unit
) {
    val categoriasExistentes by achadosVM.todasCategorias.collectAsState()
    var nome by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "📁 Nova Categoria",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        OutlinedTextField(
            value = nome,
            onValueChange = {
                nome = it
                erro = null
            },
            label = { Text("Nome da categoria") },
            placeholder = { Text("Ex: Óculos") },
            singleLine = true,
            isError = erro != null,
            supportingText = erro?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleMedium,
            leadingIcon = {
                if (nome.isNotBlank()) {
                    Text(emojiParaCategoria(nome), style = MaterialTheme.typography.headlineMedium)
                }
            }
        )

        Button(
            onClick = {
                val n = nome.trim()
                val jaExiste = categoriasExistentes.any { it.nome.equals(n, ignoreCase = true) }
                when {
                    n.isEmpty() -> erro = "Digite um nome."
                    jaExiste    -> erro = "Já existe uma categoria com esse nome."
                    else -> {
                        achadosVM.adicionarCategoria(n)
                        onCategoriaSalva()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(36.dp)
        ) {
            Text(
                text = "✓ Criar Categoria",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        OutlinedButton(
            onClick = onVoltar,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("Cancelar", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Categorias existentes:",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        LazyColumn(
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categoriasExistentes, key = { it.id }) { cat ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (cat.ativa) MaterialTheme.colorScheme.surface
                                         else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "${emojiParaCategoria(cat.nome)}  ${cat.nome}" +
                                if (!cat.ativa) "  (desativada)" else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
