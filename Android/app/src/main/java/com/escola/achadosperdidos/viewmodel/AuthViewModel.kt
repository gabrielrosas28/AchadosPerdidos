package com.escola.achadosperdidos.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface EstadoAuth {
    data object Publico : EstadoAuth

    data class AguardandoPinGestor(val tentativasErradas: Int = 0) : EstadoAuth

    data object Gestor : EstadoAuth

    data class AguardandoPinSaidaQuiosque(val tentativasErradas: Int = 0) : EstadoAuth

    data object QuiosqueLiberado : EstadoAuth
}

sealed interface EventoAuth {
    data object PinIncorreto : EventoAuth
    data object AcessoConcedidoGestor : EventoAuth
}

class AuthViewModel : ViewModel() {

    private val pinValido: String = "chiara123"

    private val _estado = MutableStateFlow<EstadoAuth>(EstadoAuth.Publico)
    val estado: StateFlow<EstadoAuth> = _estado.asStateFlow()

    private val _eventos = MutableSharedFlow<EventoAuth>(extraBufferCapacity = 1)
    val eventos: SharedFlow<EventoAuth> = _eventos.asSharedFlow()

    fun solicitarEntradaGestor() {
        if (_estado.value == EstadoAuth.Publico) {
            _estado.value = EstadoAuth.AguardandoPinGestor()
        }
    }

    fun solicitarSaidaQuiosque() {
        if (_estado.value == EstadoAuth.Publico || _estado.value == EstadoAuth.Gestor) {
            _estado.value = EstadoAuth.AguardandoPinSaidaQuiosque()
        }
    }

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
            EstadoAuth.Publico,
            EstadoAuth.Gestor,
            EstadoAuth.QuiosqueLiberado -> Unit
        }
    }

    fun cancelarPin() {
        if (_estado.value is EstadoAuth.AguardandoPinGestor ||
            _estado.value is EstadoAuth.AguardandoPinSaidaQuiosque
        ) {
            _estado.value = EstadoAuth.Publico
        }
    }

    fun sairModoGestor() {
        if (_estado.value == EstadoAuth.Gestor) {
            _estado.value = EstadoAuth.Publico
        }
    }

    fun saidaQuiosqueProcessada() {
        if (_estado.value == EstadoAuth.QuiosqueLiberado) {
            _estado.value = EstadoAuth.Publico
        }
    }
}
