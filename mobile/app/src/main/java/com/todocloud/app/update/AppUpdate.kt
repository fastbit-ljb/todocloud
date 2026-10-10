package com.todocloud.app.update

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.todocloud.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Fetches GET /app/version. Returns null on any failure; an update check must never bother the user. */
object UpdateChecker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    fun fetchBlocking(): AppVersionInfo? = runCatching {
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/app/version")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            parse(JSONObject(response.body?.string().orEmpty()))
        }
    }.getOrNull()

    suspend fun fetch(): AppVersionInfo? = withContext(Dispatchers.IO) { fetchBlocking() }

    internal fun parse(json: JSONObject): AppVersionInfo? {
        val latest = json.optString("latest_version").trim()
        if (latest.isEmpty()) return null
        fun optional(name: String) = json.optString(name).takeIf { !json.isNull(name) && it.isNotBlank() }
        return AppVersionInfo(
            latestVersion = latest,
            minSupportedVersion = optional("min_supported_version"),
            releaseNotes = json.optString("release_notes"),
            releaseUrl = json.optString("release_url"),
            downloadUrl = optional("download_url"),
        )
    }
}

/** Remembers what the user already saw so the prompt is not repeated. */
class UpdateStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("app_update", Context.MODE_PRIVATE)

    var skippedVersion: String?
        get() = preferences.getString("skipped_version", null)
        set(value) = preferences.edit().putString("skipped_version", value).apply()

    var lastPromptDay: String?
        get() = preferences.getString("last_prompt_day", null)
        set(value) = preferences.edit().putString("last_prompt_day", value).apply()

    var notifiedVersion: String?
        get() = preferences.getString("notified_version", null)
        set(value) = preferences.edit().putString("notified_version", value).apply()

    /** Latest version seen by any check; drives the badge on the settings entry. */
    var latestKnownVersion: String?
        get() = preferences.getString("latest_known_version", null)
        set(value) = preferences.edit().putString("latest_known_version", value).apply()

    companion object {
        fun today(): String = LocalDate.now().toString()
    }
}

/** Notification channel, the update notification itself, and the daily background check. */
object UpdateNotifier {
    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 0x7C0D_0001
    private const val CHECK_REQUEST_CODE = 0x7C0D_0002
    private const val FIRST_CHECK_DELAY_MILLIS = 6 * 60 * 60 * 1000L

    fun openUrlIntent(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun showUpdateNotification(context: Context, info: AppVersionInfo) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "版本更新", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "TodoCloud 有新版本时提醒"
                },
            )
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openUrlIntent(info.openUrl),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val summary = UpdatePolicy.summarizeNotes(info.releaseNotes, maxChars = 200)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("TodoCloud ${info.latestVersion} 可以更新了")
            .setContentText(summary.lineSequence().firstOrNull() ?: "点击下载新版本")
            .setStyle(Notification.BigTextStyle().bigText(summary.ifBlank { "点击下载新版本" }))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    /** Idempotent: keeps an existing schedule instead of pushing it back on every launch. */
    fun scheduleDailyCheck(context: Context, replace: Boolean = false) {
        val intent = Intent(context, UpdateCheckReceiver::class.java)
        val existing = PendingIntent.getBroadcast(
            context, CHECK_REQUEST_CODE, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (existing != null && !replace) return
        val pending = PendingIntent.getBroadcast(
            context, CHECK_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(AlarmManager::class.java).setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + FIRST_CHECK_DELAY_MILLIS,
            AlarmManager.INTERVAL_DAY,
            pending,
        )
    }
}

/** Daily background check: notifies once per new version. */
class UpdateCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        thread(name = "todocloud-update-check") {
            try {
                val info = UpdateChecker.fetchBlocking() ?: return@thread
                val store = UpdateStore(appContext)
                store.latestKnownVersion = info.latestVersion
                if (UpdatePolicy.shouldNotify(info, UpdateChecker.currentVersion, store.notifiedVersion)) {
                    UpdateNotifier.showUpdateNotification(appContext, info)
                    store.notifiedVersion = info.latestVersion
                }
            } finally {
                pending.finish()
            }
        }
    }
}
