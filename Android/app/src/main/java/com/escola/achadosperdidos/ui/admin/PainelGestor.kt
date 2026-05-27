package com.escola.achadosperdidos.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escola.achadosperdidos.data.model.StatusItem
import com.escola.achadosperdidos.viewmodel.AchadosViewModel

// ════════════════════════════════════════════════════════════════════════════
//  Rotas internas do Painel
// ════════════════════════════════════════════════════════════════════════════

private sealed interface RotaAdmin {
    data object Menu : RotaAdmin
    data object NovoAchado : RotaAdmin
    data object NovaCategoria : RotaAdmin
    data object EntregarItem : RotaAdmin
    data object Historico : RotaAdmin
    data object ExportarBackup : RotaAdmin
}

// ════════════════════════════════════════════════════════════════════════════
//  Root do Painel do Gestor
// ════════════════════════════════════════════════════════════════════════════

/**
 * Painel do Gestor — entrada após autenticação por PIN.
 *
 * Contém 4 ações principais (mockup):
 *  - ➕ Novo Achado    → cadastra item com câmera
 *  - 📁 Nova Categoria → cria categoria dinâmica
 *  - ✔️ Entregar Item  → dá baixa (status → Devolvido)
 *  - 📜 Histórico      → lista devoluções
 */
@Composable
fun PainelGestor(
    achadosVM: AchadosViewModel,
    onSair: () -> Unit
) {
    var rota by remember { mutableStateOf<RotaAdmin>(RotaAdmin.Menu) }

    // Botão voltar físico: subtelas voltam ao menu; no menu, fecha o modo gestor.
    BackHandler {
        if (rota == RotaAdmin.Menu) {
            achadosVM.limparFiltros()
            onSair()
        } else {
            achadosVM.limparFiltros()
            rota = RotaAdmin.Menu
        }
    }

    // Reseta filtros ao sair do modo gestor.
    LaunchedEffect(Unit) {
        achadosVM.limparFiltros()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopoGestor(
            tituloSubtela = quandoSubtela(rota),
            mostrarVoltar = rota != RotaAdmin.Menu,
            onVoltar = {
                achadosVM.limparFiltros()
                rota = RotaAdmin.Menu
            },
            onSair = {
                achadosVM.limparFiltros()
                onSair()
            }
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (rota) {
                RotaAdmin.Menu -> MenuGestor(
                    onNovoAchado     = { rota = RotaAdmin.NovoAchado },
                    onNovaCategoria  = { rota = RotaAdmin.NovaCategoria },
                    onEntregarItem   = { rota = RotaAdmin.EntregarItem },
                    onHistorico      = { rota = RotaAdmin.Historico },
                    onExportarBackup = { rota = RotaAdmin.ExportarBackup }
                )
                RotaAdmin.NovoAchado -> NovoAchado(
                    achadosVM    = achadosVM,
                    onVoltar     = { rota = RotaAdmin.Menu },
                    onItemSalvo  = { rota = RotaAdmin.Menu }
                )
                RotaAdmin.NovaCategoria -> NovaCategoria(
                    achadosVM         = achadosVM,
                    onVoltar          = { rota = RotaAdmin.Menu },
                    onCategoriaSalva  = { rota = RotaAdmin.Menu }
                )
                RotaAdmin.EntregarItem -> EntregarItem(achadosVM = achadosVM)
                RotaAdmin.Historico    -> Historico(achadosVM = achadosVM)
                RotaAdmin.ExportarBackup -> ExportarBackup()
            }
        }
    }
}

private fun quandoSubtela(r: RotaAdmin): String? = when (r) {
    RotaAdmin.Menu           -> null
    RotaAdmin.NovoAchado     -> "Novo Achado"
    RotaAdmin.NovaCategoria  -> "Nova Categoria"
    RotaAdmin.EntregarItem   -> "Entregar Item"
    RotaAdmin.Historico      -> "Histórico"
    RotaAdmin.ExportarBackup -> "Exportar Backup"
}

// ════════════════════════════════════════════════════════════════════════════
//  Top bar do Painel
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun TopoGestor(
    tituloSubtela: String?,
    mostrarVoltar: Boolean,
    onVoltar: () -> Unit,
    onSair: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (tituloSubtela == null) "👤 Painel do Gestor" else "👤 $tituloSubtela",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            if (mostrarVoltar) {
                TextButton(onClick = onVoltar) {
                    Text(
                        text  = "Menu",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.size(4.dp))
            }

            TextButton(onClick = onSair) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text  = "Sair",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Menu de 4 botões grandes
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun MenuGestor(
    onNovoAchado: () -> Unit,
    onNovaCategoria: () -> Unit,
    onEntregarItem: () -> Unit,
    onHistorico: () -> Unit,
    onExportarBackup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        BotaoMenuGestor(
            emoji = "➕",
            titulo = "Novo Achado",
            subtitulo = "Abre a câmera e cadastra um item",
            onClick = onNovoAchado
        )
        BotaoMenuGestor(
            emoji = "📁",
            titulo = "Nova Categoria",
            subtitulo = "Cria um novo módulo",
            onClick = onNovaCategoria
        )
        BotaoMenuGestor(
            emoji = "✔️",
            titulo = "Entregar Item",
            subtitulo = "Dar baixa em um item encontrado",
            onClick = onEntregarItem
        )
        BotaoMenuGestor(
            emoji = "📜",
            titulo = "Histórico",
            subtitulo = "Ver tudo que já foi devolvido",
            onClick = onHistorico
        )
        BotaoMenuGestor(
            emoji = "💾",
            titulo = "Exportar Backup",
            subtitulo = "Salva tudo num ZIP (dados + fotos)",
            onClick = onExportarBackup
        )
    }
}

@Composable
private fun BotaoMenuGestor(
    emoji: String,
    titulo: String,
    subtitulo: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, style = TextStyle(fontSize = 56.sp))
            Spacer(Modifier.size(20.dp))
            Column {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitulo,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
