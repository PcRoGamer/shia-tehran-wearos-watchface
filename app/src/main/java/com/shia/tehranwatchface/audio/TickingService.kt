package com.shia.tehranwatchface.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.shia.tehranwatchface.R
import com.shia.tehranwatchface.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TickingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var soundPool: SoundPool
    private lateinit var audioManager: AudioManager
    private lateinit var notifications: NotificationManager
    private lateinit var power: PowerManager
    private val samples = IntArray(2)
    private var loadedSamples = 0

    private val tick = object : Runnable {
        override fun run() {
            if (!power.isInteractive) {
                stopSelf()
                return
            }

            if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
                notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
            ) {
                val beat = ((System.currentTimeMillis() / 1_000L) % samples.size).toInt()
                soundPool.play(samples[beat], TICK_VOLUME, TICK_VOLUME, 1, 0, 1f)
            }
            scheduleTick()
        }
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(tick)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        notifications = getSystemService(NotificationManager::class.java)
        power = getSystemService(PowerManager::class.java)
        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        ContextCompat.registerReceiver(
            this,
            screenOff,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !power.isInteractive) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (active.value) return START_NOT_STICKY

        startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        running.value = true
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status != 0) {
                stopSelf()
            } else if (++loadedSamples == samples.size) {
                scheduleTick()
            }
        }
        samples[0] = soundPool.load(this, R.raw.clock_tick, 1)
        samples[1] = soundPool.load(this, R.raw.clock_tock, 1)
        if (samples.any { it == 0 }) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        unregisterReceiver(screenOff)
        soundPool.setOnLoadCompleteListener(null)
        soundPool.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        running.value = false
        super.onDestroy()
    }

    private fun scheduleTick() {
        // Re-align to each wall-clock second so handler delays don't accumulate.
        handler.postDelayed(tick, 1_000L - System.currentTimeMillis() % 1_000L)
    }

    private fun createNotification(): Notification {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.ticking_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, TickingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_crescent)
            .setContentTitle(getString(R.string.ticking_active))
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.ticking_stop), stop).build())
            .build()
    }

    companion object {
        private const val ACTION_STOP = "com.shia.tehranwatchface.action.STOP_TICKING"
        private const val CHANNEL_ID = "clock_ticking"
        private const val NOTIFICATION_ID = 1
        private const val TICK_VOLUME = 0.5f
        private val running = MutableStateFlow(false)
        val active = running.asStateFlow()
    }
}
