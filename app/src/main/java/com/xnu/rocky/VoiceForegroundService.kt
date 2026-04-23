//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//

package com.xnu.rocky

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Keeps the voice session alive when the user leaves the app or locks the screen.
 *
 * iOS only lets backgrounded audio apps *play* — they cannot sustain a full mic-in voice session.
 * Android's foreground-service-with-microphone lets Rocky stay in an active conversation while
 * the user walks around, switches apps, or turns the screen off. Users see a persistent
 * notification with a "Stop" button they can tap from the shade or lock screen.
 */
class VoiceForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // Hand off to MainActivity so SessionRuntime (the single source of truth for voice
                // lifecycle) can tear down the bridge cleanly; it then calls stop() on this service.
                val stopIntent = Intent(this, MainActivity::class.java)
                stopIntent.action = ACTION_STOP_VOICE
                stopIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(stopIntent)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startAsForeground()
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, VoiceForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_voice)
            .setContentTitle(getString(R.string.voice_service_title))
            .setContentText(getString(R.string.voice_service_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPending)
            .addAction(0, getString(R.string.voice_service_stop), stopPending)
            .setSilent(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.voice_service_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.voice_service_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "openrocky_voice_session"
        private const val NOTIF_ID = 1001
        private const val ACTION_STOP = "com.xnu.rocky.action.STOP_VOICE_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, VoiceForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceForegroundService::class.java))
        }
    }
}
