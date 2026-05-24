package com.escola.achadosperdidos.ui.publico

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.escola.achadosperdidos.R

/**
 * Diálogo de digitação do PIN do gestor.
 *
 * Usado em dois contextos (controlados pela [AuthViewModel]):
 *  1. Acesso ao Modo Gestor (click no hambúrguer)
 *  2. Saída do quiosque (long-click 3s no mascote)
 *
 * O conteúdo é o mesmo — só o título muda. A validação acontece **fora**
 * deste composable (no `onConfirmar`).
 */
@Composable
fun ModalPin(
    titulo: String,
    onConfirmar: (String) -> Unit,
    onCancelar: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    // Foca automaticamente o campo ao abrir
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = {
            Text(
                text = titulo,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "Digite a senha de acesso:",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { novo -> pin = novo.trim() },
                    label = { Text(stringResource(R.string.pin_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                    textStyle = MaterialTheme.typography.titleLarge
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pinAtual = pin
                    pin = ""
                    onConfirmar(pinAtual)
                },
                enabled = pin.isNotEmpty()
            ) {
                Text(
                    text = stringResource(R.string.pin_confirmar),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        dismissButton = {
            TextButton(onClick = {
                pin = ""
                onCancelar()
            }) {
                Text(
                    text = stringResource(R.string.pin_cancelar),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    )
}
