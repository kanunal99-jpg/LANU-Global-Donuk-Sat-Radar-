package com.lanu.globaldonuksatisradari.crm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.lanu.globaldonuksatisradari.MainActivity
import java.util.concurrent.TimeUnit
import kotlin.math.max

class CrmNextActionReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {
    override fun doWork(): Result {
        val actionId = inputData.getString(KEY_ACTION_ID).orEmpty()
        val customerName = inputData.getString(KEY_CUSTOMER_NAME).orEmpty().ifBlank { "CRM müşterisi" }
        val actionLabel = inputData.getString(KEY_ACTION_LABEL).orEmpty().ifBlank { "Takip" }
        val dueAtEpochMs = inputData.getLong(KEY_DUE_AT, Long.MIN_VALUE)

        CrmReminderNotifier.notifyIfNeeded(
            context = applicationContext,
            actionId = actionId,
            customerName = customerName,
            actionLabel = actionLabel,
            dueAtEpochMs = dueAtEpochMs,
        )
        return Result.success()
    }

    companion object {
        private const val KEY_ACTION_ID = "action_id"
        private const val KEY_CUSTOMER_NAME = "customer_name"
        private const val KEY_ACTION_LABEL = "action_label"
        private const val KEY_DUE_AT = "due_at"

        fun input(
            actionId: String,
            customerName: String,
            actionLabel: String,
            dueAtEpochMs: Long,
        ): Data =
            Data.Builder()
                .putString(KEY_ACTION_ID, actionId)
                .putString(KEY_CUSTOMER_NAME, customerName)
                .putString(KEY_ACTION_LABEL, actionLabel)
                .putLong(KEY_DUE_AT, dueAtEpochMs)
                .build()
    }
}

class CrmReminderRecoveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!CrmReminderNotifier.permissionGranted(applicationContext)) {
            return Result.success()
        }

        val database = LanuCrmDatabase.getInstance(applicationContext)
        val activeOwner = SupabaseAuthClient(applicationContext).session.value?.userId
        val due = database.nextActionDao().due(
            nowEpochMs = System.currentTimeMillis(),
        )
        var deliveredThisRun = 0

        for (action in due) {
            if (deliveredThisRun >= MAX_ACTIONS_PER_SCAN) break
            val customer = database.customerDao().findById(action.customerId) ?: continue
            if (!customer.mergedIntoCustomerId.isNullOrBlank()) continue

            val ownerMatches = if (activeOwner.isNullOrBlank()) {
                customer.ownerUserId.isNullOrBlank()
            } else {
                customer.ownerUserId == activeOwner
            }
            if (!ownerMatches) continue

            val type = runCatching { CrmNextActionType.valueOf(action.type) }
                .getOrDefault(CrmNextActionType.NOTE)
            val notified = CrmReminderNotifier.notifyIfNeeded(
                context = applicationContext,
                actionId = action.id,
                customerName = customer.businessName,
                actionLabel = CrmReminderNotifier.label(type),
                dueAtEpochMs = action.dueAtEpochMs,
            )
            if (notified) deliveredThisRun += 1
        }
        return Result.success()
    }

    companion object {
        private const val MAX_ACTIONS_PER_SCAN = 50
    }
}

internal object CrmReminderNotifier {
    private const val CHANNEL_ID = "crm_next_actions"
    private const val PREFS_NAME = "lanu_crm_reminder_delivery"

    fun permissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "CRM Takip Hatırlatmaları",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Planlanan arama, ziyaret, teklif ve sipariş takipleri"
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyIfNeeded(
        context: Context,
        actionId: String,
        customerName: String,
        actionLabel: String,
        dueAtEpochMs: Long,
    ): Boolean {
        if (actionId.isBlank() || !permissionGranted(context)) return false
        if (alreadyDelivered(context, actionId, dueAtEpochMs)) return false

        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("crm_action_id", actionId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            actionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("CRM Takibi • $actionLabel")
            .setContentText(customerName)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$customerName için $actionLabel zamanı geldi.",
                ),
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(actionId.hashCode(), notification)
            markDelivered(context, actionId, dueAtEpochMs)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun clear(context: Context, actionId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(prefKey(actionId))
            .apply()
    }

    fun label(type: CrmNextActionType): String = when (type) {
        CrmNextActionType.CALL -> "Arama"
        CrmNextActionType.VISIT -> "Ziyaret"
        CrmNextActionType.MEETING -> "Görüşme"
        CrmNextActionType.SAMPLE_FOLLOW_UP -> "Numune takibi"
        CrmNextActionType.PROPOSAL_FOLLOW_UP -> "Teklif takibi"
        CrmNextActionType.ORDER_FOLLOW_UP -> "Sipariş takibi"
        CrmNextActionType.NOTE -> "Takip"
    }

    private fun alreadyDelivered(context: Context, actionId: String, dueAtEpochMs: Long): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(prefKey(actionId), Long.MIN_VALUE) == dueAtEpochMs

    private fun markDelivered(context: Context, actionId: String, dueAtEpochMs: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(prefKey(actionId), dueAtEpochMs)
            .apply()
    }

    private fun prefKey(actionId: String): String = "delivered:$actionId"
}

object CrmNextActionReminderScheduler {
    fun schedule(context: Context, action: CrmNextAction, customerName: String) {
        CrmReminderNotifier.ensureChannel(context)
        CrmReminderNotifier.clear(context, action.id)
        val delay = max(0L, action.dueAtEpochMs - System.currentTimeMillis())
        val request = OneTimeWorkRequestBuilder<CrmNextActionReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                CrmNextActionReminderWorker.input(
                    actionId = action.id,
                    customerName = customerName,
                    actionLabel = CrmReminderNotifier.label(action.type),
                    dueAtEpochMs = action.dueAtEpochMs,
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(action.id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context, actionId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(actionId))
        NotificationManagerCompat.from(context).cancel(actionId.hashCode())
        CrmReminderNotifier.clear(context, actionId)
    }

    private fun workName(actionId: String): String = "crm-next-action-reminder-$actionId"
}

object CrmReminderRecoveryScheduler {
    private const val UNIQUE_WORK = "lanu_crm_reminder_recovery"

    fun schedule(context: Context) {
        CrmReminderNotifier.ensureChannel(context.applicationContext)
        val request = PeriodicWorkRequestBuilder<CrmReminderRecoveryWorker>(
            15,
            TimeUnit.MINUTES,
        ).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    internal fun uniqueWorkName(): String = UNIQUE_WORK
}
