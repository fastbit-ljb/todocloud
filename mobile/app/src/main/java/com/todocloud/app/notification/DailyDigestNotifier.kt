package com.todocloud.app.notification

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.todocloud.app.MainActivity
import com.todocloud.app.ui.dailyDigest
import com.todocloud.app.widget.TaskCalendarWidget
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Morning summary ("今天 3 件事…"), sent once a day from the task list cached for the
 * widgets, so it needs no network. Nothing is sent on a day with nothing due or overdue.
 */
object DailyDigestNotifier {
    private const val CHANNEL_ID = "daily_digest"
    private const val NOTIFICATION_ID = 0x7C0D_0101
    private const val ALARM_REQUEST_CODE = 0x7C0D_0102
    private const val PREFS = "daily_digest"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_DAY = "last_day"
    val DIGEST_TIME: LocalTime = LocalTime.of(8, 0)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) schedule(context, replace = true) else cancel(context)
    }

    /** Idempotent: keeps an existing alarm unless [replace]. */
    fun schedule(context: Context, replace: Boolean = false) {
        if (!isEnabled(context)) return
        val intent = Intent(context, DailyDigestReceiver::class.java)
        val existing = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (existing != null && !replace) return
        val pending = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(AlarmManager::class.java).setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextTriggerMillis(),
            AlarmManager.INTERVAL_DAY,
            pending,
        )
    }

    private fun cancel(context: Context) {
        val intent = Intent(context, DailyDigestReceiver::class.java)
        PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { pending ->
            context.getSystemService(AlarmManager::class.java).cancel(pending)
            pending.cancel()
        }
    }

    private fun nextTriggerMillis(): Long {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var next = now.toLocalDate().atTime(DIGEST_TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.atZone(zone).toInstant().toEpochMilli()
    }

    internal fun showIfNeeded(context: Context) {
        if (!isEnabled(context)) return
        val today = LocalDate.now()
        val preferences = prefs(context)
        // Inexact alarms can fire twice or late; one summary per day, and none after noon.
        if (preferences.getString(KEY_LAST_DAY, null) == today.toString()) return
        if (LocalTime.now().isAfter(LocalTime.NOON)) return
        val digest = dailyDigest(TaskCalendarWidget.loadCachedTasks(context), today) ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "每日摘要", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "每天早上 8 点告诉你今天要做的事"
                },
            )
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_INITIAL_TAB, 0)
        }
        val contentIntent = PendingIntent.getActivity(
            context, NOTIFICATION_ID, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = digest.lines.joinToString("\n").ifBlank { "打开 TodoCloud 看看要不要重新安排" }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle(digest.title)
            .setContentText(digest.lines.firstOrNull() ?: body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        preferences.edit().putString(KEY_LAST_DAY, today.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class DailyDigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DailyDigestNotifier.showIfNeeded(context.applicationContext)
    }
}
