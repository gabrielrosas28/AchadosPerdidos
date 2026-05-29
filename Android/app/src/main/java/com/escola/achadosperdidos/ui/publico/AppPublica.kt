package com.escola.achadosperdidos.ui.publico

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.escola.achadosperdidos.R
import com.escola.achadosperdidos.data.local.ItemComCategoria
import com.escola.achadosperdidos.ui.admin.PainelGestor
import com.escola.achadosperdidos.viewmodel.AchadosViewModel
import com.escola.achadosperdidos.viewmodel.AuthViewModel
import com.escola.achadosperdidos.viewmodel.EstadoAuth
import com.escola.achadosperdidos.viewmodel.EventoAuth

private sealed interface RotaPublica {
    data object Categorias : RotaPublica
    data class Itens(val catId: Long, val catNome: String) : RotaPublica
    data class Detalhe(val item: ItemComCategoria) : RotaPublica
}

@Composable
fun AppPublica(
    onSairQuiosque: () -> Unit,
    achadosVM: AchadosViewModel = viewModel(),
    authVM: AuthViewModel = viewModel()
) {
    val estado by authVM.estado.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    BackHandler(enabled = true) { }

    val msgPinErrado     = stringResource(R.string.pin_incorreto)
    val msgAcessoGestor  = stringResource(R.string.pin_acesso_concedido)

    LaunchedEffect(Unit) {
        authVM.eventos.collect { ev ->
            when (ev) {
                EventoAuth.PinIncorreto -> snackbar.showSnackbar(msgPinErrado)
                EventoAuth.AcessoConcedidoGestor -> snackbar.showSnackbar(msgAcessoGestor)
            }
        }
    }

    LaunchedEffect(estado) {
        if (estado == EstadoAuth.QuiosqueLiberado) {
            onSairQuiosque()
            authVM.saidaQuiosqueProcessada()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {

            when (estado) {
                EstadoAuth.Gestor -> {
                    PainelGestor(
                        achadosVM = achadosVM,
                        onSair    = authVM::sairModoGestor
                    )
                }
                else -> {
                    TelaPublicaRaiz(
                        achadosVM         = achadosVM,
                        onHamburguer      = authVM::solicitarEntradaGestor,
                        onMascoteLongClick = authVM::solicitarSaidaQuiosque
                    )
                    val tituloModal = when (estado) {
                        is EstadoAuth.AguardandoPinGestor          -> stringResource(R.string.pin_titulo_gestor)
                        is EstadoAuth.AguardandoPinSaidaQuiosque   -> stringResource(R.string.pin_titulo_quiosque)
                        else                                       -> null
                    }
                    if (tituloModal != null) {
                        ModalPin(
                            titulo      = tituloModal,
                            onConfirmar = authVM::validarPin,
                            onCancelar  = authVM::cancelarPin
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelaPublicaRaiz(
    achadosVM: AchadosViewModel,
    onHamburguer: () -> Unit,
    onMascoteLongClick: () -> Unit
) {
    var pilha by remember { mutableStateOf<List<RotaPublica>>(listOf(RotaPublica.Categorias)) }
    val rotaAtual = pilha.last()

    BackHandler(enabled = pilha.size > 1) {
        pilha = pilha.dropLast(1)
    }

    Column(Modifier.fillMaxSize()) {
        TopoPublico(
            mostrarVoltar     = pilha.size > 1,
            tituloSecundario  = (rotaAtual as? RotaPublica.Itens)?.catNome,
            onVoltar          = { pilha = pilha.dropLast(1) },
            onMascoteLongClick = onMascoteLongClick,
            onHamburguer      = onHamburguer
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val rota = rotaAtual) {
                RotaPublica.Categorias -> {
                    LaunchedEffect(Unit) {
                        achadosVM.selecionarCategoria(null)
                    }
                    val categorias by achadosVM.categorias.collectAsState()
                    GradeCategorias(
                        categorias = categorias,
                        onClick    = { cat ->
                            achadosVM.selecionarCategoria(cat.id)
                            pilha = pilha + RotaPublica.Itens(cat.id, cat.nome)
                        }
                    )
                }
                is RotaPublica.Itens -> {
                    LaunchedEffect(rota.catId) {
                        achadosVM.selecionarCategoria(rota.catId)
                    }
                    val itens by achadosVM.itens.collectAsState()
                    GradeItens(
                        itens   = itens,
                        onClick = { pilha = pilha + RotaPublica.Detalhe(it) }
                    )
                }
                is RotaPublica.Detalhe -> {
                    DetalheItem(item = rota.item)
                }
            }
        }
    }
}

@Composable
private fun TopoPublico(
    mostrarVoltar: Boolean,
    tituloSecundario: String?,
    onVoltar: () -> Unit,
    onMascoteLongClick: () -> Unit,
    onHamburguer: () -> Unit
) {
    Surface(
        color           = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Mascote(
                onLongClick = onMascoteLongClick,
                modifier    = Modifier.size(72.dp)
            )

            if (mostrarVoltar) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onVoltar) {
                    Text(
                        text  = stringResource(R.string.texto_voltar),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                if (tituloSecundario != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = tituloSecundario,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            IconButton(onClick = onHamburguer) {
                Icon(
                    imageVector        = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.cd_menu_gestor),
                    tint               = MaterialTheme.colorScheme.onPrimary,
                    modifier           = Modifier.size(40.dp)
                )
            }
        }
    }
}

@Composable
private fun Mascote(
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duracaoMs = 3000
    var pressionando by remember { mutableStateOf(false) }
    val progresso = remember { Animatable(0f) }

    LaunchedEffect(pressionando) {
        if (pressionando) {
            progresso.snapTo(0f)
            progresso.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = duracaoMs, easing = LinearEasing)
            )
            onLongClick()
            pressionando = false
        } else {
            progresso.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onPrimary)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressionando = true
                    waitForUpOrCancellation()
                    pressionando = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.mascote),
            contentDescription = stringResource(R.string.cd_mascote),
            modifier = Modifier.fillMaxSize().clip(CircleShape)
        )
        if (pressionando) {
            CircularProgressIndicator(
                progress    = { progresso.value },
                modifier    = Modifier.fillMaxSize(),
                color       = MaterialTheme.colorScheme.error,
                strokeWidth = 5.dp
            )
        }
    }
}


