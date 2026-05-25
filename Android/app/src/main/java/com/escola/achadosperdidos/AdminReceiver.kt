package com.escola.achadosperdidos

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver que registra o app como **Device Admin** (e quando promovido,
 * Device Owner) no Android.
 *
 * ## Para que serve
 *
 * Sem este receiver registrado e promovido a Device Owner, o `startLockTask()`
 * cai no modo "screen pinning" — que mostra um diálogo de confirmação e
 * pode ser destravado pelo usuário com Back+Recents.
 *
 * Com o app como Device Owner:
 *  - `startLockTask()` entra **silenciosamente** no modo quiosque.
 *  - Botões físicos (Home, Recents, Volume) ficam bloqueados.
 *  - Notificações, status bar e quick settings são desabilitados.
 *  - O app pode ser definido como Home/Launcher padrão — abre sozinho no boot.
 *
 * ## Como promover a Device Owner
 *
 * Ver `TUTORIAL-DEVICE-OWNER.md` na raiz do projeto. Resumo:
 *  1. Factory reset no tablet, NÃO logar conta Google no setup.
 *  2. Ativar USB debug.
 *  3. Instalar o APK.
 *  4. `adb shell dpm set-device-owner com.escola.achadosperdidos/.AdminReceiver`
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin habilitado")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device Admin desabilitado")
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Log.i(TAG, "Entrou em LockTask: $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Log.i(TAG, "Saiu do LockTask")
    }

    companion object {
        private const val TAG = "AdminReceiver"

        /** Componente usado em `dpm set-device-owner` e nas chamadas do DPM. */
        fun componente(context: Context): ComponentName =
            ComponentName(context, AdminReceiver::class.java)
    }
}
