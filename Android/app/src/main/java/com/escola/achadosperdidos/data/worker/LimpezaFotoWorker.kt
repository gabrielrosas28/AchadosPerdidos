package com.escola.achadosperdidos.data.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.escola.achadosperdidos.AchadosPerdidosApp
import com.escola.achadosperdidos.data.network.ApiClient
import com.escola.achadosperdidos.data.network.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Worker periódico que executa duas tarefas de manutenção diariamente:
 *
 * **1. Limpeza de fotos locais**
 *    Remove do armazenamento interno do tablet as fotos de itens que:
 *    - Foram marcados como **Devolvido** pelo gestor; OU
 *    - Têm **mais de 180 dias** de cadastro.
 *    Após apagar o arquivo, limpa a coluna `caminhoFoto` no Room.
 *
 * **2. Sincronização com o servidor** (best-effort)
 *    Tenta enviar itens e categorias pendentes ao servidor Windows.
 *    Falhas de rede não impedem o resultado SUCCESS; o WorkManager reagendará.
 *
 * Registro: chame [LimpezaFotoWorker.agendar] na inicialização do app
 * (ver [AchadosPerdidosApp]).
 */
class LimpezaFotoWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "LimpezaFotoWorker"

        /** Nome único para [WorkManager.enqueueUniquePeriodicWork]. */
        const val WORK_NAME = "limpeza_foto_diaria"

        /**
         * Agenda o worker para executar uma vez por dia, apenas quando a bateria
         * não estiver baixa. Usa [ExistingPeriodicWorkPolicy.KEEP] para não
         * substituir um agendamento existente após reinicialização do app.
         */
        fun agendar(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<LimpezaFotoWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.i(TAG, "Worker de limpeza de fotos agendado (1× por dia).")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Iniciando ciclo de manutenção...")

        val app       = applicationContext as AchadosPerdidosApp
        val itemDao   = app.database.itemDao()
        val catDao    = app.database.categoriaDao()

        // ── 1. Limpeza de fotos ───────────────────────────────────────────────
        val limite180Dias = Date(System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000)
        val paraLimpar    = itemDao.obterParaLimpezaFoto(limite180Dias)

        var fotosApagadas = 0
        paraLimpar.forEach { item ->
            val caminho = item.caminhoFoto ?: return@forEach
            try {
                val arquivo = File(caminho)
                if (arquivo.exists()) {
                    if (arquivo.delete()) {
                        fotosApagadas++
                        Log.d(TAG, "Foto apagada: $caminho (item id=${item.id})")
                    } else {
                        Log.w(TAG, "Não foi possível apagar: $caminho")
                    }
                }
                // Atualiza o Room independentemente (arquivo pode já ter sido apagado antes)
                itemDao.limparCaminhoFoto(item.id)
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao processar foto do item id=${item.id}: ${e.message}")
            }
        }

        Log.i(TAG, "Limpeza concluída: $fotosApagadas/${paraLimpar.size} fotos apagadas.")

        // ── 2. Sincronização com o servidor (best-effort) ─────────────────────
        try {
            // Usa o singleton (já configurado em AchadosPerdidosApp.onCreate).
            val sync = SyncRepository(ApiClient.obter(), catDao, itemDao, applicationContext)
            sync.sincronizarTudo()
            Log.i(TAG, "Sincronização com servidor concluída.")
        } catch (e: Exception) {
            // Falha de rede não bloqueia o sucesso da limpeza já realizada.
            Log.w(TAG, "Sync falhou (será retentado no próximo ciclo): ${e.message}")
        }

        Result.success()
    }
}
