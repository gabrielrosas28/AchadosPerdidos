package com.escola.achadosperdidos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

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
