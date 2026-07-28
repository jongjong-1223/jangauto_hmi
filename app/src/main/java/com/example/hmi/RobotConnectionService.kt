package com.example.hmi

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.hmi.model.ControlRequest

/**
 * Foreground Service to maintain Robot connection and heartbeat
 * even when the app is in background or screen is off.
 */
class RobotConnectionService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val CHANNEL_ID = "RobotConnectionChannel"
    private val NOTIFICATION_ID = 1

    private val txRunnable = object : Runnable {
        override fun run() {
            sendCurrentCommand()
            handler.postDelayed(this, Config.TX_PERIOD_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        handler.removeCallbacks(txRunnable)
        handler.post(txRunnable)
        
        return START_STICKY
    }

    private fun sendCurrentCommand() {
        SocketManager.send(ControlRequest(
            swBits = CommandState.requestedSwBits,
            keyBits = CommandState.keyBits,
            speedBits = CommandState.speedBits,
            videoBit = if (CommandState.isVideoOn) 1 else 0,
            safeBit = if (CommandState.isSafeMode) 1 else 0
        ))
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // This is called when the user swipes away the app from recents
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(txRunnable)
        SocketManager.stop()
        CommandState.reset()
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Robot Connected")
            .setContentText("Maintaining active control session...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Robot Connection Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
