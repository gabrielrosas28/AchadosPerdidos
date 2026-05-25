package com.escola.achadosperdidos

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
 * **modo quiosque real** (Device Owner + lock task) do tablet.
 *
 * ## Modo Quiosque
 *
 * - **Device Owner** (modo de produção, recomendado): o app foi promovido a
 *   device-owner via `adb shell dpm set-device-owner com.escola.achadosperdidos/.AdminReceiver`
 *   (ver `TUTORIAL-DEVICE-OWNER.md`). Nesse modo:
 *     - [aplicarPoliticasDeviceOwner] roda no `onCreate` e:
 *         - registra o próprio package em `setLockTaskPackages`,
 *         - desabilita a status bar,
 *         - define o app como Home/Launcher padrão (abre sozinho no boot),
 *         - configura quais "features" do sistema continuam acessíveis dentro do lock task.
 *     - `startLockTask` entra silenciosamente, sem diálogo.
 *     - Botões Home/Recents/Volume/Back ficam bloqueados.
 *     - Notificações e quick settings desabilitados.
 *
 * - **Sem Device Owner** (testes): cai no modo "screen pinning". O sistema
 *   mostra um diálogo de confirmação e o pin pode ser quebrado com
 *   Back+Recents. Útil só para desenvolvimento.
 *
 * Saída legítima do quiosque: long-press de 3s no mascote → PIN `chiara123`
 * → [stopLockTask] + [finishAffinity].
 */
class MainActivity : ComponentActivity() {

    private val dpm by lazy { getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    private val adminComponent: ComponentName by lazy { AdminReceiver.componente(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: a UI desenha por baixo das barras do sistema.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        aplicarModoImersivo()

        // Aplica políticas de Device Owner, se for o caso. Idempotente.
        aplicarPoliticasDeviceOwner()

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
     * recusar o diálogo (modo screen-pinning sem Device Owner), o app continua
     * funcionando sem trava real.
     */
    private fun tentarIniciarLockTask() {
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                startLockTask()
                Log.i(TAG, "startLockTask() chamado")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Não foi possível entrar em lockTask: ${t.message}")
        }
    }

    /**
     * Se o app for Device Owner, aplica as políticas necessárias para um
     * quiosque verdadeiro. Sem efeito caso o app ainda não tenha sido
     * promovido — então é seguro chamar sempre.
     */
    private fun aplicarPoliticasDeviceOwner() {
        if (!dpm.isDeviceOwnerApp(packageName)) {
            Log.i(TAG, "Não é Device Owner — pulando políticas. " +
                "Promova com: adb shell dpm set-device-owner $packageName/.AdminReceiver")
            return
        }
        Log.i(TAG, "Device Owner detectado — aplicando políticas de quiosque")

        runCatching {
            // 1. Whitelist do próprio pacote para lock task (silencioso, sem diálogo).
            dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))

            // 2. Define quais "features" do sistema ficam visíveis durante lock task.
            //    NONE = nada (sem status bar, sem notificações, sem home button).
            //    Em produção isso é o que segura o quiosque.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(
                    adminComponent,
                    DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
                )
            }

            // 3. Esconde a status bar permanentemente (impede swipe-down do quick settings).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setStatusBarDisabled(adminComponent, true)
            }

            // 4. Vira o app o Home/Launcher padrão — abre sozinho no boot e quando
            //    o usuário aperta o botão Home (caso ainda exista).
            val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(
                adminComponent,
                homeFilter,
                ComponentName(packageName, MainActivity::class.java.name)
            )

            // 5. Desativa o assistente de voz / overlays do sistema dentro do app.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching {
                    dpm.addUserRestriction(adminComponent, "no_create_windows")
                    dpm.addUserRestriction(adminComponent, "no_safe_boot")
                    dpm.addUserRestriction(adminComponent, "no_factory_reset")
                    dpm.addUserRestriction(adminComponent, "no_add_user")
                    dpm.addUserRestriction(adminComponent, "no_install_unknown_sources")
                }.onFailure { Log.w(TAG, "Falha ao aplicar user restrictions: ${it.message}") }
            }

            Log.i(TAG, "Políticas de Device Owner aplicadas com sucesso")
        }.onFailure {
            Log.e(TAG, "Erro ao aplicar políticas de Device Owner", it)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
