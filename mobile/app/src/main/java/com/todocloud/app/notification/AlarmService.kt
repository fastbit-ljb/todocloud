package com.todocloud.app.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator

class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRinging()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_MUTE) {
            player?.setVolume(0f, 0f)
            vibrator?.cancel()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_UNMUTE) {
            player?.setVolume(1f, 1f)
            startVibration()
            return START_NOT_STICKY
        }

        val taskId = intent?.getIntExtra(ReminderReceiver.EXTRA_TASK_ID, 0) ?: 0
        val title = intent?.getStringExtra(ReminderReceiver.EXTRA_TITLE) ?: "任务提醒"
        val description = intent?.getStringExtra(ReminderReceiver.EXTRA_DESCRIPTION)
        val steps = intent?.getStringExtra(ReminderReceiver.EXTRA_STEPS)
        if (taskId == 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        stopRinging()
        ReminderScheduler.createAlarmServiceChannel(this)
        startForegroundSafely(taskId, title)
        acquireWakeLock()
        startSound()
        startVibration()
        openAlarmWindow(taskId, title, description, steps)
        return START_NOT_STICKY
    }

    private fun startForegroundSafely(taskId: Int, title: String) {
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            taskId,
            AlarmActivity.intent(this, taskId, title),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, ReminderScheduler.ALARM_SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("闹钟正在响铃")
            .setContentText(title)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setFullScreenIntent(fullScreenIntent, true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun openAlarmWindow(taskId: Int, title: String, description: String?, steps: String?) {
        runCatching {
            startActivity(
                AlarmActivity.intent(this, taskId, title, description, steps).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    private fun acquireWakeLock() {
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "TodoCloud:Alarm",
            )
            ?.apply { acquire(10 * 60 * 1000L) }
    }

    private fun startSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmService, Uri.parse(uri.toString()))
                isLooping = true
                prepare()
                start()
            }
        }.onFailure {
            player?.release()
            player = null
        }
    }

    private fun startVibration() {
        val deviceVibrator = getSystemService(Vibrator::class.java) ?: return
        vibrator = deviceVibrator
        runCatching {
            val pattern = longArrayOf(0L, 700L, 350L, 700L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                deviceVibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                deviceVibrator.vibrate(pattern, 0)
            }
        }
    }

    private fun stopRinging() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        wakeLock?.let { lock -> if (lock.isHeld) lock.release() }
        wakeLock = null
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 0x5443
        const val ACTION_STOP = "com.todocloud.app.notification.STOP_ALARM"
        const val ACTION_MUTE = "com.todocloud.app.notification.MUTE_ALARM"
        const val ACTION_UNMUTE = "com.todocloud.app.notification.UNMUTE_ALARM"
    }
}
