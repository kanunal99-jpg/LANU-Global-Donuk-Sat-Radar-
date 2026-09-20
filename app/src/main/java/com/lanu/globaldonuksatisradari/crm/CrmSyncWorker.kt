package com.lanu.globaldonuksatisradari.crm

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Runs the local CRM queue only when network connectivity is available.
 * The default remote is intentionally unconfigured until an authorized backend exists.
 */
class CrmSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = LanuCrmDatabase.getInstance(applicationContext)
        val remote = SupabaseCrmRemoteDataSource(SupabaseAuthClient(applicationContext))
        val pullResult = remote.pullInto(database)
        if (pullResult is RemotePullResult.RetryableFailure) return Result.retry()

        val engine = CrmSyncEngine(
            syncDao = database.syncOperationDao(),
            remote = remote,
            stateStore = RoomCrmSyncStateStore(database),
        )
        val results = engine.processBatch()

        return if (results.any { it is SyncProcessResult.Deferred }) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}

/** Safe default provider; backend integration replaces this without changing worker scheduling. */
object CrmSyncRemoteProvider {
    @Volatile
    var dataSource: RemoteCrmDataSource = UnconfiguredRemoteCrmDataSource
}

object CrmSyncScheduler {
    private const val WORK_NAME = "lanu_global_donuk_crm_sync"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<CrmSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30L,
                TimeUnit.SECONDS,
            )
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
