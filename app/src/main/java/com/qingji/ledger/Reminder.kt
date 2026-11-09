package com.qingji.ledger

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.*

object ReminderScheduler {
    const val CHANNEL = "daily_ledger"
    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "记账提醒", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "在设定时间附近提醒记账，允许系统延迟" })
    }
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    @Synchronized fun schedule(context: Context, settings: Settings) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val base = Intent(context, ReminderReceiver::class.java)
        val old = PendingIntent.getBroadcast(context, 41, base, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (old != null) { manager.cancel(old); old.cancel() }
        if (!allowed(context)) return
        val next = nextReminder(settings) ?: return
        val intent = base.putExtra("scheduledDate", next.toLocalDate().toString()).putExtra("revision", settings.reminderRevision)
        val pending = PendingIntent.getBroadcast(context, 41, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending)
    }
    fun post(context: Context) {
        if (!allowed(context)) return
        val click = Intent(context, MainActivity::class.java).putExtra("new_entry", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 42, click, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("今天的账记好了吗？").setContentText("花一点时间，把今天的生活记清楚。").setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try { NotificationManagerCompat.from(context).notify(41, notification) } catch (_: SecurityException) { /* The user may revoke the permission between the check and notify. */ }
    }
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as LedgerApp
        app.applicationScope.launch { try {
            withTimeout(8500) {
                val settings = app.repository.settings.flow.first()
                val now = ZonedDateTime.now()
                val date = now.toLocalDate().toString()
                val isValid = settings.enabled && intent.getLongExtra("revision", -1) == settings.reminderRevision && intent.getStringExtra("scheduledDate") == date && now.toLocalTime() < LocalTime.of(23, 0) && now.dayOfWeek.value in settings.days && settings.lastSentDate != date
                if (isValid && ReminderScheduler.allowed(context) && (!settings.skip || app.repository.dao.countOn(date) == 0)) {
                    app.repository.settings.update { it.copy(lastSentDate = date) }
                    ReminderScheduler.post(context)
                }
                ReminderScheduler.schedule(context, app.repository.settings.flow.first())
            }
        } finally { pending.finish() } }
    }
}
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync(); val app = context.applicationContext as LedgerApp
        app.applicationScope.launch { try { withTimeout(8500) { ReminderScheduler.schedule(context, app.repository.settings.flow.first()) } } finally { pending.finish() } }
    }
}
