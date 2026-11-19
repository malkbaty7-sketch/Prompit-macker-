package com.forgeprom.codestudio.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * خدمة العمل في الخلفية (ForegroundGenerationService)
 * متوافقة بالكامل مع Android 8–15+ (API 26–35) باستخدام ServiceCompat مع FOREGROUND_SERVICE_TYPE_DATA_SYNC،
 * ومعالجة آمنة لإيقاف الخدمة دون حدوث ForegroundServiceDidNotStartInTimeException.
 */
class ForegroundGenerationService : Service() {

    companion object {
        private const val TAG = "ForgepromFGS"
        const val CHANNEL_ID = "forgeprom_background_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.forgeprom.codestudio.action.START_TASK"
        const val ACTION_STOP = "com.forgeprom.codestudio.action.STOP_TASK"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_STATUS = "extra_task_status"

        fun startTask(context: Context, title: String, status: String) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, ForegroundGenerationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TASK_TITLE, title)
                putExtra(EXTRA_TASK_STATUS, status)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start foreground service safely: ${e.message}")
            }
        }

        fun stopTask(context: Context) {
            val appContext = context.applicationContext
            try {
                appContext.stopService(Intent(appContext, ForegroundGenerationService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Unable to stop foreground service safely: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TASK_TITLE) ?: "Forgeprom Code Studio"
        val status = intent?.getStringExtra(EXTRA_TASK_STATUS) ?: "جارٍ معالجة المهام الهندسية في الخلفية..."

        val notification = buildNotification(title, status)
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(title: String, status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "مهام التوليد وتنزيل النماذج",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعارات حماية التوليد الطويل وتنزيل نماذج الذكاء الاصطناعي المحلية"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
