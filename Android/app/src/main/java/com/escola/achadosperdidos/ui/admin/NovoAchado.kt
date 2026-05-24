package com.escola.achadosperdidos.ui.admin

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.escola.achadosperdidos.data.model.Categoria
import com.escola.achadosperdidos.viewmodel.AchadosViewModel
import java.io.File

/**
 * Tela do gestor para cadastrar um novo item achado.
 *
 * Fluxo:
 *  1. Botão "Tirar Foto" → verifica permissão CAMERA → abre câmera nativa
 *  2. Câmera grava direto no File preparado pelo [FotoStorage]
 *  3. Foto aparece no preview
 *  4. Gestor preenche descrição, local (opcional) e seleciona categoria
 *  5. "Salvar" chama [AchadosViewModel.adicionarItem] e volta ao menu
 */
@Composable
fun NovoAchado(
    achadosVM: AchadosViewModel,
    onVoltar: () -> Unit,
    onItemSalvo: () -> Unit
) {
    val context = LocalContext.current
    val categorias by achadosVM.categorias.collectAsState()

    var descricao by remember { mutableStateOf("") }
    var local by remember { mutableStateOf("") }
    var catSelecionada by remember { mutableStateOf<Categoria?>(null) }
    var dropdownAberto by remember { mutableStateOf(false) }
    var arquivoFoto by remember { mutableStateOf<File?>(null) }
    var uriDestino by remember { mutableStateOf<Uri?>(null) }

    // ── Launcher da câmera ──────────────────────────────────────────────────
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { sucesso ->
        if (!sucesso) {
            // Usuário cancelou ou erro — descarta o arquivo temporário
            arquivoFoto?.delete()
            arquivoFoto = null
            uriDestino = null
        }
    }

    // ── Launcher de permissão da câmera ─────────────────────────────────────
    val permissaoCamera = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (concedida) {
            val (arq, uri) = FotoStorage.criarArquivoTempFoto(context)
            arquivoFoto = arq
            uriDestino = uri
            cameraLauncher.launch(uri)
        }
    }

    fun abrirCamera() {
        val temPermissao = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (temPermissao) {
            val (arq, uri) = FotoStorage.criarArquivoTempFoto(context)
            arquivoFoto = arq
            uriDestino = uri
            cameraLauncher.launch(uri)
        } else {
            permissaoCamera.launch(Manifest.permission.CAMERA)
        }
    }

    // Pré-seleciona a primeira categoria quando carregam
    LaunchedEffect(categorias) {
        if (catSelecionada == null && categorias.isNotEmpty()) {
            catSelecionada = categorias.first()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "➕ Novo Achado",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        // ── Preview / câmera ────────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val foto = arquivoFoto
                if (foto != null && foto.exists()) {
                    AsyncImage(
                        model = foto,
                        contentDescription = "Foto do item",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷", style = TextStyle(fontSize = 80.sp))
                        Text(
                            "Sem foto ainda",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Button(
            onClick = { abrirCamera() },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(30.dp)
        ) {
            Text(
                text = if (arquivoFoto != null) "🔁 Tirar outra foto" else "📷 Tirar foto",
                style = MaterialTheme.typography.titleMedium
            )
        }

        // ── Campos ──────────────────────────────────────────────────────────
        OutlinedTextField(
            value = descricao,
            onValueChange = { descricao = it },
            label = { Text("Descrição do item") },
            placeholder = { Text("Ex: Lancheira do Homem-Aranha azul") },
            singleLine = false,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = local,
            onValueChange = { local = it },
            label = { Text("Onde foi encontrado (opcional)") },
            placeholder = { Text("Ex: Pátio do recreio") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleMedium
        )

        // ── Dropdown de categoria (Box + OutlinedButton + DropdownMenu) ─────
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { dropdownAberto = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = catSelecionada?.let { "Categoria: ${it.nome}" } ?: "Selecione a categoria",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null
                )
            }
            DropdownMenu(
                expanded = dropdownAberto,
                onDismissRequest = { dropdownAberto = false }
            ) {
                categorias.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat.nome, style = MaterialTheme.typography.titleMedium) },
                        onClick = {
                            catSelecionada = cat
                            dropdownAberto = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Ações ───────────────────────────────────────────────────────────
        val podeSalvar = descricao.isNotBlank() && catSelecionada != null
        Button(
            onClick = {
                achadosVM.adicionarItem(
                    descricao = descricao,
                    localEncontrado = local.ifBlank { null },
                    categoriaId = catSelecionada!!.id,
                    caminhoFoto = arquivoFoto?.absolutePath
                )
                onItemSalvo()
            },
            enabled = podeSalvar,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(36.dp)
        ) {
            Text(
                text = "✓ Salvar",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        OutlinedButton(
            onClick = {
                arquivoFoto?.delete()
                onVoltar()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(30.dp)
        ) {
            Text("Cancelar", style = MaterialTheme.typography.titleMedium)
        }
    }
}
