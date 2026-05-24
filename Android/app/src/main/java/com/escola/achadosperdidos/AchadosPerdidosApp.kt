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

        // Configura o cliente HTTP da API. Em produção, esses valores devem ser lidos
        // das SharedPreferences (configuráveis pelo gestor nas telas admin do Passo 6).
        // - BASE_URL_PADRAO = http://10.0.2.2:5080/ (emulador → localhost do PC)
        // - Troque para o IP real do servidor da escola antes de instalar no tablet.
        // - X-Api-Key precisa bater com a chave do appsettings.json do servidor.
        ApiClient.configurar(
            baseUrl = ApiClient.BASE_URL_PADRAO,
            apiKey  = ""    // setar a API Key real quando estiver disponível
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
