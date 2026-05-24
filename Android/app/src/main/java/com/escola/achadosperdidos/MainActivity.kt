package com.escola.achadosperdidos

import android.app.ActivityManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.escola.achadosperdidos.ui.publico.AppPublica
import com.escola.achadosperdidos.ui.theme.AchadosPerdidosTheme

/**
 * Única Activity do app. Hospeda toda a UI em Jetpack Compose e gerencia o
 * **modo quiosque** (lock task) do tablet.
 *
 * ## Modo Quiosque
 *
 * A cada `onResume` o app tenta entrar em [startLockTask] — o que efetivamente
 * trava o tablet neste app:
 *
 *  - **Device Owner** (recomendado em produção): se o app foi promovido a
 *    device-owner via `adb shell dpm set-device-owner com.escola.achadosperdidos/.AdminReceiver`,
 *    o lock task entra sem qualquer diálogo. Botões Home/Recents/Volume/Back ficam
 *    completamente bloqueados.
 *
 *  - **Sem Device Owner** (testes): na primeira chamada o sistema mostra uma
 *    confirmação "App ficará fixado nesta tela". Após o usuário aceitar, o app
 *    fica pinned; pode ser destravado segurando Back+Recents juntos.
 *
 * Para SAIR do quiosque legitimamente: long-press de 3s no mascote → PIN
 * `chiara123` → [stopLockTask] + [finishAffinity].
 *
 * ## Imersivo
 *
 * Status bar e navigation bar ficam escondidas (aparecem brevemente ao deslizar).
 * Reaplicado em `onResume` porque diálogos do sistema podem restaurar as barras.
 *
 * ## Back Button
 *
 * Bloqueado no nível raiz por um `BackHandler` em `AppPublica` (o composable
 * envoltório). Só o long-press no mascote permite sair.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: a UI desenha por baixo das barras do sistema.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        aplicarModoImersivo()

        setContent {
            AchadosPerdidosTheme {
                AppPublica(
                    onSairQuiosque = {
                        // Chamado quando o gestor valida o PIN para sair do quiosque.
                        runCatching { stopLockTask() }
                        finishAffinity()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tentarIniciarLockTask()
        // Diálogos do sistema (ex: pedido de permissão de câmera) podem ter
        // restaurado as barras — reaplica o modo imersivo.
        aplicarModoImersivo()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) aplicarModoImersivo()
    }

    /** Esconde status bar e navigation bar (modo imersivo "lean back"). */
    private fun aplicarModoImersivo() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /**
     * Tenta iniciar o lock task. Não-fatal: se o device não suportar ou o usuário
     * recusar o diálogo, o app continua funcionando (sem trava real).
     */
    private fun tentarIniciarLockTask() {
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            // minSdk = 24 — sempre temos lockTaskModeState (API 23+).
            if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                startLockTask()
                Log.i(TAG, "startLockTask() chamado")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Não foi possível entrar em lockTask: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
