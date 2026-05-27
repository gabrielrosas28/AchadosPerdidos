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

    // Scope de processo — sobrevive a mudanças de configuração; cancelado quando o processo morre.
    val applicationScope = CoroutineScope(SupervisorJob())

    // Banco acessível por toda a app via (applicationContext as AchadosPerdidosApp).database
    val database: AppDatabase by lazy { AppDatabase.obter(this, applicationScope) }

    /**
     * Identificador único e estável deste tablet (UUID v4) — gerado na primeira execução
     * e persistido em SharedPreferences. Usado no campo [data.model.Item.tabletId] e
     * enviado em todas as sincronizações para que o servidor distinga itens criados
     * por tablets diferentes (idempotência por `tabletId + idLocalTablet`).
     */
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

        // BuildConfig.ACHADOS_BASE_URL / ACHADOS_API_KEY sao injetados pelo
        // build.gradle.kts a partir de local.properties (nao versionado).
        // Trocar de rede = editar local.properties + recompilar.
        ApiClient.configurar(
            baseUrl = BuildConfig.ACHADOS_BASE_URL,
            apiKey  = BuildConfig.ACHADOS_API_KEY
        )

        // Agenda o worker de limpeza de fotos + sync para rodar 1× por dia.
        // KEEP policy: se já estiver agendado, não substitui.
        LimpezaFotoWorker.agendar(this)
    }

    companion object {
        private const val PREFS_NOME = "achados_perdidos_prefs"
        private const val CHAVE_TABLET_ID = "tablet_id"
    }
}
