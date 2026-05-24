package com.escola.achadosperdidos.ui.publico

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.escola.achadosperdidos.R
import com.escola.achadosperdidos.data.local.ItemComCategoria

/**
 * Tela cheia mostrando a foto e a descrição de um item.
 *
 * Toda a interação que importa neste passo é o botão **"ESSE ITEM É SEU?"**
 * que abre um pop-up orientando o usuário a procurar a diretoria. A baixa
 * do item (status → Devolvido) acontece somente no Modo Gestor (Passo 6).
 */
@Composable
fun DetalheItem(
    item: ItemComCategoria,
    modifier: Modifier = Modifier
) {
    var mostrarPopup by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Foto (ou placeholder) ──────────────────────────────────────────
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (item.item.caminhoFoto != null) {
                    AsyncImage(
                        model = item.item.caminhoFoto,
                        contentDescription = item.item.descricao,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷", style = TextStyle(fontSize = 96.sp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.sem_foto),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Descrição ──────────────────────────────────────────────────────
        Text(
            text = item.item.descricao,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        if (item.item.localEncontrado != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Encontrado em: ${item.item.localEncontrado}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Botão de ação ──────────────────────────────────────────────────
        Button(
            onClick = { mostrarPopup = true },
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(80.dp),
            shape = RoundedCornerShape(40.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor   = MaterialTheme.colorScheme.onError
            )
        ) {
            Text(
                text = stringResource(R.string.botao_esse_item_e_seu),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // ── Popup "Dirija-se à diretoria" ──────────────────────────────────────
    if (mostrarPopup) {
        AlertDialog(
            onDismissRequest = { mostrarPopup = false },
            icon = { Text("🏫", style = TextStyle(fontSize = 56.sp)) },
            title = {
                Text(
                    text = stringResource(R.string.dialogo_retirar_titulo),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialogo_retirar_texto),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { mostrarPopup = false }) {
                    Text(
                        text = stringResource(R.string.dialogo_ok),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        )
    }
}
