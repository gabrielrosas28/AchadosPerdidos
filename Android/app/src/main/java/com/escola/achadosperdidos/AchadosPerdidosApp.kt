package com.escola.achadosperdidos

import android.app.Application
import com.escola.achadosperdidos.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class AchadosPerdidosApp : Application() {

    // Scope de processo — sobrevive a configuração; cancelado quando o processo morre.
    val applicationScope = CoroutineScope(SupervisorJob())

    // Banco acessível por toda a app via (applicationContext as AchadosPerdidosApp).database
    val database: AppDatabase by lazy { AppDatabase.obter(this, applicationScope) }
}
