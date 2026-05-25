package com.escola.achadosperdidos.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escola.achadosperdidos.data.model.Categoria
import com.escola.achadosperdidos.ui.publico.EMOJIS_SUGERIDOS
import com.escola.achadosperdidos.ui.publico.emojiDe
import com.escola.achadosperdidos.ui.publico.emojiParaCategoria
import com.escola.achadosperdidos.viewmodel.AchadosViewModel

/**
 * Tela do gestor para **criar e editar** categorias.
 *
 * Layout:
 *  - Form de criação no topo (nome + emoji escolhível)
 *  - Lista das categorias existentes — **tocar num item abre o dialog de edição**
 *
 * Dialog de edição:
 *  - Edita nome + emoji
 *  - Botão para desativar (ou reativar, se já está desativada)
 */
@Composable
fun NovaCategoria(
    achadosVM: AchadosViewModel,
    onVoltar: () -> Unit,
    onCategoriaSalva: () -> Unit
) {
    val categoriasExistentes by achadosVM.todasCategorias.collectAsState()

    var nome by remember { mutableStateOf("") }
    var emojiEscolhido by remember { mutableStateOf<String?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }
    var emEdicao by remember { mutableStateOf<Categoria?>(null) }

    // Quando o gestor não escolheu emoji ainda, sugerimos o heurístico do nome.
    val emojiPreview: String = emojiEscolhido?.takeIf { it.isNotBlank() }
        ?: emojiParaCategoria(nome.ifBlank { "outros" })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "📁 Categorias",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        // ── Form de criação ────────────────────────────────────────────────
        OutlinedTextField(
            value = nome,
            onValueChange = {
                nome = it
                erro = null
            },
            label = { Text("Nome da nova categoria") },
            placeholder = { Text("Ex: Óculos") },
            singleLine = true,
            isError = erro != null,
            supportingText = erro?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleMedium,
            leadingIcon = {
                Text(emojiPreview, style = MaterialTheme.typography.headlineMedium)
            }
        )

        Text(
            text = "Emoji (toque para escolher):",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        SeletorEmoji(
            selecionado = emojiEscolhido,
            onSelecionar = { emojiEscolhido = it }
        )

        Button(
            onClick = {
                val n = nome.trim()
                val jaExiste = categoriasExistentes.any { it.nome.equals(n, ignoreCase = true) }
                when {
                    n.isEmpty() -> erro = "Digite um nome."
                    jaExiste    -> erro = "Já existe uma categoria com esse nome."
                    else -> {
                        achadosVM.adicionarCategoria(n, emojiEscolhido)
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
            text = "Categorias existentes (toque para editar):",
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
                    onClick = { emEdicao = cat },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (cat.ativa) MaterialTheme.colorScheme.surface
                                         else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(emojiDe(cat), style = TextStyle(fontSize = 32.sp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = cat.nome + if (!cat.ativa) "  (desativada)" else "",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "✏️",
                            style = TextStyle(fontSize = 22.sp)
                        )
                    }
                }
            }
        }
    }

    // ── Dialog de edição ───────────────────────────────────────────────────
    emEdicao?.let { cat ->
        DialogEditarCategoria(
            categoria = cat,
            outrasCategorias = categoriasExistentes.filter { it.id != cat.id },
            onSalvar = { novoNome, novoEmoji ->
                achadosVM.editarCategoria(cat.id, novoNome, novoEmoji)
                emEdicao = null
            },
            onDesativar = {
                achadosVM.desativarCategoria(cat.id)
                emEdicao = null
            },
            onReativar = {
                achadosVM.reativarCategoria(cat.id)
                emEdicao = null
            },
            onCancelar = { emEdicao = null }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Dialog de edição
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun DialogEditarCategoria(
    categoria: Categoria,
    outrasCategorias: List<Categoria>,
    onSalvar: (novoNome: String, novoEmoji: String?) -> Unit,
    onDesativar: () -> Unit,
    onReativar: () -> Unit,
    onCancelar: () -> Unit
) {
    var nome by remember { mutableStateOf(categoria.nome) }
    var emoji by remember { mutableStateOf(categoria.emoji) }
    var erro by remember { mutableStateOf<String?>(null) }

    val emojiPreview: String = emoji?.takeIf { it.isNotBlank() }
        ?: emojiParaCategoria(nome)

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Editar categoria", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = {
                        nome = it
                        erro = null
                    },
                    label = { Text("Nome") },
                    singleLine = true,
                    isError = erro != null,
                    supportingText = erro?.let { { Text(it) } },
                    leadingIcon = {
                        Text(emojiPreview, style = MaterialTheme.typography.headlineMedium)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.titleMedium
                )

                Text(
                    "Emoji:",
                    style = MaterialTheme.typography.titleSmall
                )
                SeletorEmoji(
                    selecionado = emoji,
                    onSelecionar = { emoji = it }
                )

                if (categoria.ativa) {
                    TextButton(
                        onClick = onDesativar,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🚫 Desativar categoria")
                    }
                } else {
                    TextButton(
                        onClick = onReativar,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✅ Reativar categoria")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val n = nome.trim()
                val duplicado = outrasCategorias.any { it.nome.equals(n, ignoreCase = true) }
                when {
                    n.isEmpty() -> erro = "Digite um nome."
                    duplicado   -> erro = "Já existe outra categoria com esse nome."
                    else        -> onSalvar(n, emoji)
                }
            }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        }
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  Seletor de emoji (grade compacta)
// ════════════════════════════════════════════════════════════════════════════

/**
 * Grade de emojis sugeridos + opção "Auto" (sem emoji fixo, deixa a heurística
 * decidir pelo nome). O selecionado fica com fundo destacado.
 *
 * Implementada como [Column] de [Row]s para evitar import-clash entre as
 * funções `items` de LazyListScope e LazyGridScope.
 */
@Composable
private fun SeletorEmoji(
    selecionado: String?,
    onSelecionar: (String?) -> Unit
) {
    // "Auto" + 20 emojis, 7 por linha = 3 linhas
    val celulas: List<Pair<String, String?>> = buildList {
        add("auto" to null)
        EMOJIS_SUGERIDOS.forEach { add(it to it) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        celulas.chunked(7).forEach { linha ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                linha.forEach { (rotulo, valor) ->
                    BotaoEmoji(
                        texto = rotulo,
                        fontSize = if (valor == null) 12.sp else 28.sp,
                        selecionado = if (valor == null) selecionado.isNullOrBlank()
                                      else valor == selecionado,
                        onClick = { onSelecionar(valor) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BotaoEmoji(
    texto: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    selecionado: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selecionado) MaterialTheme.colorScheme.primaryContainer
             else MaterialTheme.colorScheme.surfaceVariant
    Surface(
        color = bg,
        shape = CircleShape,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = texto,
                style = TextStyle(
                    fontSize = fontSize,
                    color = if (selecionado) MaterialTheme.colorScheme.onPrimaryContainer
                            else Color.Unspecified
                )
            )
        }
    }
}
