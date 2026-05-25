package com.escola.achadosperdidos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Recebe `BOOT_COMPLETED` e abre a [MainActivity] imediatamente apos o boot.
 *
 * Complementa o fato de a MainActivity ser declarada com category HOME no
 * manifest — quando o app e Device Owner e Home padrao, ja abre sozinho.
 * Este receiver e um fallback para casos em que outro launcher esteja ativo
 * (ex: durante o setup inicial, antes do dpm set-device-owner).
 *
 * O sistema so chama este receiver depois que o usuario abriu o app pelo
 * menos uma vez (regra de Android 3.1+ sobre apps "stopped").
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }
        Log.i(TAG, "Boot detectado — abrindo MainActivity")
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        runCatching { context.startActivity(launch) }
            .onFailure { Log.w(TAG, "Falha ao iniciar MainActivity no boot: ${it.message}") }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
