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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lanu.globaldonuksatisradari.MainActivity
import com.lanu.globaldonuksatisradari.R
import java.util.concurrent.TimeUnit

class CrmReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        createChannel(applicationContext)
        if (!notificationPermissionGranted(applicationContext)) {
            return Result.success()
        }

        val database = LanuCrmDatabase.getInstance(applicationContext)
        val activeOwner = SupabaseAuthClient(applicationContext).session.value?.userId
        val due = database.nextActionDao().due(
            nowEpochMs = System.currentTimeMillis(),
            limit = MAX_REMINDERS_PER_RUN,
        )
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        due.forEach { action ->
            val customer = database.customerDao().findById(action.customerId) ?: return@forEach
            if (customer.ownerUserId != activeOwner) return@forEach

            val prefKey = "notified:" + action.id
            if (prefs.getLong(prefKey, Long.MIN_VALUE) == action.dueAtEpochMs) {
                return@forEach
            }

            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                action.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val type = runCatching { CrmNextActionType.valueOf(action.type) }
                .getOrDefault(CrmNextActionType.NOTE)
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(customer.businessName)
                .setContentText(reminderLabel(type) + action.note?.let { " • " + it }.orEmpty())
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        reminderLabel(type) +
                            action.note?.let { "\n" + it }.orEmpty(),
                    ),
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notified = try {
                NotificationManagerCompat.from(applicationContext)
                    .notify(action.id.hashCode(), notification)
                true
            } catch (_: SecurityException) {
                false
            }
            if (notified) {
                prefs.edit().putLong(prefKey, action.dueAtEpochMs).apply()
            }
        }
        return Result.success()
    }

    private fun reminderLabel(type: CrmNextActionType): String = when (type) {
        CrmNextActionType.CALL -> "Arama zamanı"
        CrmNextActionType.VISIT -> "Ziyaret zamanı"
        CrmNextActionType.MEETING -> "Görüşme zamanı"
        CrmNextActionType.SAMPLE_FOLLOW_UP -> "Numune takip zamanı"
        CrmNextActionType.PROPOSAL_FOLLOW_UP -> "Teklif takip zamanı"
        CrmNextActionType.ORDER_FOLLOW_UP -> "Sipariş takip zamanı"
        CrmNextActionType.NOTE -> "CRM takip zamanı"
    }

    companion object {
        const val CHANNEL_ID = "crm_follow_up"
        private const val PREFS_NAME = "lanu_crm_reminder_state"
        private const val MAX_REMINDERS_PER_RUN = 50

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "CRM takip hatırlatmaları",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Planlanan arama, ziyaret, teklif ve sipariş takipleri"
                },
            )
        }

        fun notificationPermissionGranted(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
    }
}

object CrmReminderScheduler {
    private const val UNIQUE_WORK = "lanu_crm_reminder_scan"

    fun schedule(context: Context) {
        CrmReminderWorker.createChannel(context.applicationContext)
        val request = PeriodicWorkRequestBuilder<CrmReminderWorker>(
            15,
            TimeUnit.MINUTES,
        ).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
