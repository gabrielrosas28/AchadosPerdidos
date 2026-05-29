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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        aplicarModoImersivo()

        setContent {
            AchadosPerdidosTheme {
                AppPublica(
                    onSairQuiosque = {
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
        aplicarModoImersivo()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) aplicarModoImersivo()
    }

    private fun aplicarModoImersivo() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

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

    companion object {
        private const val TAG = "MainActivity"
    }
}
