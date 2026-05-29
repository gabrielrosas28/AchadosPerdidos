package com.escola.achadosperdidos

import android.app.Application
import android.content.Context
import com.escola.achadosperdidos.data.local.AppDatabase
import com.escola.achadosperdidos.data.network.ApiClient
import com.escola.achadosperdidos.data.worker.LimpezaFotoWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.util.UUID

class AchadosPerdidosApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob())

    val database: AppDatabase by lazy { AppDatabase.obter(this, applicationScope) }

    val tabletId: String by lazy {
        val prefs = getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)
        prefs.getString(CHAVE_TABLET_ID, null) ?: run {
            val novo = UUID.randomUUID().toString()
            prefs.edit().putString(CHAVE_TABLET_ID, novo).apply()
            novo
        }
    }

    override fun onCreate() {
        super.onCreate()

        ApiClient.configurar(
            baseUrl = BuildConfig.ACHADOS_BASE_URL,
            apiKey  = BuildConfig.ACHADOS_API_KEY
        )

        LimpezaFotoWorker.agendar(this)
    }

    companion object {
        private const val PREFS_NOME = "achados_perdidos_prefs"
        private const val CHAVE_TABLET_ID = "tablet_id"
    }
}
