package com.escola.achadosperdidos.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

// ════════════════════════════════════════════════════════════════════════════
//  Estados e eventos do fluxo de autenticação
// ════════════════════════════════════════════════════════════════════════════

/**
 * Estado observável pela UI — `collectAsState()` no Compose recompõe a tela
 * quando o valor muda.
 */
sealed interface EstadoAuth {
    /** Modo padrão: alunos / pais / funcionários só visualizam. */
    data object Publico : EstadoAuth

    /** Modal de PIN aberto após click no menu hambúrguer. */
    data class AguardandoPinGestor(val tentativasErradas: Int = 0) : EstadoAuth

    /** Painel do gestor ativo — pode cadastrar, devolver, gerenciar categorias. */
    data object Gestor : EstadoAuth

    /** Modal de PIN aberto após long-click de 3 s no mascote/logo. */
    data class AguardandoPinSaidaQuiosque(val tentativasErradas: Int = 0) : EstadoAuth

    /**
     * PIN para sair do quiosque foi validado.
     * A `MainActivity` deve observar esse estado e chamar `stopLockTask()` / `finish()`
     * — depois disso, chame [AuthViewModel.saidaQuiosqueProcessada].
     */
    data object QuiosqueLiberado : EstadoAuth
}

/**
 * Eventos one-shot (não-estado) — usados para feedback transitório:
 * snackbar de erro, vibração, animação rápida etc.
 *
 * No Compose, coletar com `LaunchedEffect(Unit) { viewModel.eventos.collect { ... } }`.
 */
sealed interface EventoAuth {
    data object PinIncorreto : EventoAuth
    data object AcessoConcedidoGestor : EventoAuth
}

// ════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ════════════════════════════════════════════════════════════════════════════

/**
 * Gerencia a alternância **Modo Público × Modo Gestor** e a verificação do PIN
 * solicitada nos dois pontos de entrada restritos:
 *
 *  - **Long-click de 3 s no mascote** → solicita PIN para sair do quiosque.
 *  - **Click no menu hambúrguer**     → solicita PIN para entrar como gestor.
 *
 * O Compose observa [estado] e troca a árvore de telas conforme o `when` abaixo:
 *
 * ```kotlin
 * @Composable
 * fun App() {
 *     val authVM: AuthViewModel = viewModel()
 *     val estado by authVM.estado.collectAsState()
 *
 *     // Snackbar para erros transitórios
 *     val snackbar = remember { SnackbarHostState() }
 *     LaunchedEffect(Unit) {
 *         authVM.eventos.collect { ev ->
 *             when (ev) {
 *                 EventoAuth.PinIncorreto -> snackbar.showSnackbar("PIN incorreto")
 *                 EventoAuth.AcessoConcedidoGestor -> { /* feedback opcional */ }
 *             }
 *         }
 *     }
 *
 *     when (estado) {
 *         EstadoAuth.Gestor -> PainelGestor(onSair = authVM::sairModoGestor)
 *         EstadoAuth.QuiosqueLiberado -> {
 *             SideEffect {
 *                 activity.stopLockTask()
 *                 activity.finishAffinity()
 *                 authVM.saidaQuiosqueProcessada()
 *             }
 *         }
 *         else -> TelaPublica(
 *             estado = estado,
 *             onHamburguerClick = authVM::solicitarEntradaGestor,
 *             onMascoteLongClick = authVM::solicitarSaidaQuiosque,
 *             onPinSubmit = authVM::validarPin,
 *             onPinCancel = authVM::cancelarPin
 *         )
 *     }
 * }
 * ```
 */
class AuthViewModel : ViewModel() {

    /**
     * PIN estático provisório (regra do Passo 4).
     *
     * **TODO Passo 6:** trocar por leitura de `EncryptedSharedPreferences`,
     * configurável pelo gestor no painel.
     */
    private val pinValido: String = "chiara123"

    private val _estado = MutableStateFlow<EstadoAuth>(EstadoAuth.Publico)
    val estado: StateFlow<EstadoAuth> = _estado.asStateFlow()

    /**
     * Buffer = 1 para que [tryEmit] funcione sem suspender; eventos perdidos
     * (UI não estava coletando) são aceitáveis para feedback transitório.
     */
    private val _eventos = MutableSharedFlow<EventoAuth>(extraBufferCapacity = 1)
    val eventos: SharedFlow<EventoAuth> = _eventos.asSharedFlow()

    // ── Ações disparadas pela UI ────────────────────────────────────────────

    /** Hambúrguer clicado no Modo Público → abre o modal de PIN para gestor. */
    fun solicitarEntradaGestor() {
        if (_estado.value == EstadoAuth.Publico) {
            _estado.value = EstadoAuth.AguardandoPinGestor()
        }
    }

    /** Long-click no mascote → abre o modal de PIN para sair do quiosque. */
    fun solicitarSaidaQuiosque() {
        // Pode ser solicitado tanto no Modo Público quanto no Gestor
        if (_estado.value == EstadoAuth.Publico || _estado.value == EstadoAuth.Gestor) {
            _estado.value = EstadoAuth.AguardandoPinSaidaQuiosque()
        }
    }

    /** Submete o PIN digitado no modal. */
    fun validarPin(pin: String) {
        when (val atual = _estado.value) {
            is EstadoAuth.AguardandoPinGestor -> {
                if (pin == pinValido) {
                    _estado.value = EstadoAuth.Gestor
                    _eventos.tryEmit(EventoAuth.AcessoConcedidoGestor)
                } else {
                    _estado.value = atual.copy(
                        tentativasErradas = atual.tentativasErradas + 1
                    )
                    _eventos.tryEmit(EventoAuth.PinIncorreto)
                }
            }
            is EstadoAuth.AguardandoPinSaidaQuiosque -> {
                if (pin == pinValido) {
                    _estado.value = EstadoAuth.QuiosqueLiberado
                } else {
                    _estado.value = atual.copy(
                        tentativasErradas = atual.tentativasErradas + 1
                    )
                    _eventos.tryEmit(EventoAuth.PinIncorreto)
                }
            }
            // Estados sem modal aberto — ignora.
            EstadoAuth.Publico,
            EstadoAuth.Gestor,
            EstadoAuth.QuiosqueLiberado -> Unit
        }
    }

    /** Usuário fechou o modal sem digitar. */
    fun cancelarPin() {
        if (_estado.value is EstadoAuth.AguardandoPinGestor ||
            _estado.value is EstadoAuth.AguardandoPinSaidaQuiosque
        ) {
            _estado.value = EstadoAuth.Publico
        }
    }

    /** Botão "voltar" no painel do gestor → retorna ao Modo Público. */
    fun sairModoGestor() {
        if (_estado.value == EstadoAuth.Gestor) {
            _estado.value = EstadoAuth.Publico
        }
    }

    /**
     * Chamado pela UI após processar [EstadoAuth.QuiosqueLiberado]
     * (ou seja, depois de `stopLockTask()` / `finishAffinity()`).
     * Limpa o estado para evitar re-disparo.
     */
    fun saidaQuiosqueProcessada() {
        if (_estado.value == EstadoAuth.QuiosqueLiberado) {
            _estado.value = EstadoAuth.Publico
        }
    }
}
