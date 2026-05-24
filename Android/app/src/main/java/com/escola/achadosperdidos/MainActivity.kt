package com.escola.achadosperdidos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.escola.achadosperdidos.ui.publico.AppPublica
import com.escola.achadosperdidos.ui.theme.AchadosPerdidosTheme

/**
 * Única Activity do app. Hospeda toda a UI em Jetpack Compose.
 *
 * - **Quiosque:** o app fica fixado via lockTask em produção. A liberação
 *   acontece após PIN + long-press no mascote, e a [onSairQuiosque] passada
 *   ao [AppPublica] cuida de `stopLockTask()` e `finishAffinity()`.
 * - **Orientação:** travada em landscape pelo AndroidManifest.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge — aproveita melhor a tela do tablet
        enableEdgeToEdge()

        setContent {
            AchadosPerdidosTheme {
                AppPublica(
                    onSairQuiosque = {
                        // Sai da trava de quiosque (silenciosamente ignora se não estiver em lockTask)
                        runCatching { stopLockTask() }
                        finishAffinity()
                    }
                )
            }
        }
    }
}
