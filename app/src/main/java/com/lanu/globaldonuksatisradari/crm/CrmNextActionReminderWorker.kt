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
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
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
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        ensureChannel(applicationContext)
        val actionId = inputData.getString(KEY_ACTION_ID).orEmpty()
        val customerName = inputData.getString(KEY_CUSTOMER_NAME).orEmpty().ifBlank { "CRM müşterisi" }
        val actionLabel = inputData.getString(KEY_ACTION_LABEL).orEmpty().ifBlank { "Takip" }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("crm_action_id", actionId)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            actionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("CRM Takibi • $actionLabel")
            .setContentText(customerName)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$customerName için $actionLabel zamanı geldi."))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(actionId.hashCode(), notification)
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "crm_next_actions"
        private const val KEY_ACTION_ID = "action_id"
        private const val KEY_CUSTOMER_NAME = "customer_name"
        private const val KEY_ACTION_LABEL = "action_label"

        fun input(actionId: String, customerName: String, actionLabel: String): Data =
            Data.Builder()
                .putString(KEY_ACTION_ID, actionId)
                .putString(KEY_CUSTOMER_NAME, customerName)
                .putString(KEY_ACTION_LABEL, actionLabel)
                .build()

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
    }
}

object CrmNextActionReminderScheduler {
    fun schedule(context: Context, action: CrmNextAction, customerName: String) {
        CrmNextActionReminderWorker.ensureChannel(context)
        val delay = max(0L, action.dueAtEpochMs - System.currentTimeMillis())
        val request = OneTimeWorkRequestBuilder<CrmNextActionReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                CrmNextActionReminderWorker.input(
                    actionId = action.id,
                    customerName = customerName,
                    actionLabel = label(action.type),
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
    }

    private fun workName(actionId: String): String = "crm-next-action-reminder-$actionId"

    private fun label(type: CrmNextActionType): String = when (type) {
        CrmNextActionType.CALL -> "Arama"
        CrmNextActionType.VISIT -> "Ziyaret"
        CrmNextActionType.MEETING -> "Görüşme"
        CrmNextActionType.SAMPLE_FOLLOW_UP -> "Numune takibi"
        CrmNextActionType.PROPOSAL_FOLLOW_UP -> "Teklif takibi"
        CrmNextActionType.ORDER_FOLLOW_UP -> "Sipariş takibi"
        CrmNextActionType.NOTE -> "Takip"
    }
}
