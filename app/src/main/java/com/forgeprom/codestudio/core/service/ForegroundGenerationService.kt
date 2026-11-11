package com.forgeprom.codestudio.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * خدمة العمل في الخلفية (ForegroundGenerationService)
 * تحمي عمليات توليد البرومبتات الطويلة وتنزيل نماذج GGUF من الإغلاق بواسطة نظام أندرويد
 * عند قفل شاشة الهاتف أو الانتقال لتطبيق آخر، مع إشعار حي يوضح حالة التقدم.
 */
class ForegroundGenerationService : Service() {

    companion object {
        const val CHANNEL_ID = "forgeprom_background_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_STATUS = "extra_task_status"

        fun startTask(context: Context, title: String, status: String) {
            val intent = Intent(context, ForegroundGenerationService::class.java).apply {
                putExtra(EXTRA_TASK_TITLE, title)
                putExtra(EXTRA_TASK_STATUS, status)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTask(context: Context) {
            val intent = Intent(context, ForegroundGenerationService::class.java)
            context.stopService(intent)
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
        startForeground(NOTIFICATION_ID, notification)

        return START_NOT_STICKY
    }

    private fun buildNotification(title: String, status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
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
