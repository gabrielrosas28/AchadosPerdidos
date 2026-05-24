package com.escola.achadosperdidos

import android.app.Application
import com.escola.achadosperdidos.data.local.AppDatabase
import com.escola.achadosperdidos.data.worker.LimpezaFotoWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class AchadosPerdidosApp : Application() {

    // Scope de processo — sobrevive a mudanças de configuração; cancelado quando o processo morre.
    val applicationScope = CoroutineScope(SupervisorJob())

    // Banco acessível por toda a app via (applicationContext as AchadosPerdidosApp).database
    val database: AppDatabase by lazy { AppDatabase.obter(this, applicationScope) }

    override fun onCreate() {
        super.onCreate()

        // Agenda o worker de limpeza de fotos e sincronização para rodar 1× por dia.
        // Usa KEEP policy: se já estiver agendado, não substitui.
        LimpezaFotoWorker.agendar(this)
    }
}
