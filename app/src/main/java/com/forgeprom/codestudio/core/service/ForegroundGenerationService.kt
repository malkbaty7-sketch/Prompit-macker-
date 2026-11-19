package com.forgeprom.codestudio.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.forgeprom.codestudio.MainActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * خدمة العمل في الخلفية (ForegroundGenerationService)
 * متوافقة بالكامل مع Android 8–15+ (API 26–35) باستخدام ServiceCompat مع FOREGROUND_SERVICE_TYPE_DATA_SYNC،
 * وتدعم:
 * - شريط تقدم حي (Progress Bar) يتحدّث لحظياً بنسبة التنزيل وسرعة التحميل والوقت المتبقي.
 * - زر إيقاف مباشر داخل الإشعار لإلغاء التنزيل بأمان دون الحاجة لفتح التطبيق.
 */
class ForegroundGenerationService : Service() {

    companion object {
        private const val TAG = "ForgepromFGS"
        const val CHANNEL_ID = "forgeprom_background_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.forgeprom.codestudio.action.START_TASK"
        const val ACTION_UPDATE = "com.forgeprom.codestudio.action.UPDATE_PROGRESS"
        const val ACTION_CANCEL_FROM_NOTIFICATION = "com.forgeprom.codestudio.action.CANCEL_FROM_NOTIFICATION"
        const val ACTION_STOP = "com.forgeprom.codestudio.action.STOP_TASK"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_STATUS = "extra_task_status"
        const val EXTRA_PROGRESS_PERCENT = "extra_progress_percent"

        private val _cancelRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        val cancelRequests: SharedFlow<Unit> = _cancelRequests.asSharedFlow()

        @Volatile
        private var isRunning: Boolean = false

        fun startTask(context: Context, title: String, status: String, progressPercent: Int = -1) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, ForegroundGenerationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TASK_TITLE, title)
                putExtra(EXTRA_TASK_STATUS, status)
                putExtra(EXTRA_PROGRESS_PERCENT, progressPercent)
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

        fun updateProgress(context: Context, title: String, status: String, progressPercent: Int) {
            if (!isRunning) return
            val appContext = context.applicationContext
            try {
                val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notification = buildNotificationStatic(appContext, title, status, progressPercent)
                manager.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to update notification progress: ${e.message}")
            }
        }

        fun stopTask(context: Context) {
            val appContext = context.applicationContext
            isRunning = false
            try {
                appContext.stopService(Intent(appContext, ForegroundGenerationService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Unable to stop foreground service safely: ${e.message}")
            }
        }

        private fun buildNotificationStatic(
            context: Context,
            title: String,
            status: String,
            progressPercent: Int
        ): Notification {
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                10,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cancelIntent = Intent(context, ForegroundGenerationService::class.java).apply {
                action = ACTION_CANCEL_FROM_NOTIFICATION
            }
            val cancelPending = PendingIntent.getService(
                context,
                11,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(status)
                .setStyle(NotificationCompat.BigTextStyle().bigText(status))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(openPending)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف التنزيل", cancelPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            if (progressPercent in 0..100) {
                builder.setProgress(100, progressPercent, false)
            } else {
                builder.setProgress(0, 0, true)
            }

            return builder.build()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_CANCEL_FROM_NOTIFICATION) {
            _cancelRequests.tryEmit(Unit)
            isRunning = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TASK_TITLE) ?: "Forgeprom Code Studio"
        val status = intent?.getStringExtra(EXTRA_TASK_STATUS) ?: "جارٍ معالجة المهام الهندسية في الخلفية..."
        val progress = intent?.getIntExtra(EXTRA_PROGRESS_PERCENT, -1) ?: -1

        val notification = buildNotificationStatic(this, title, status, progress)
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
            isRunning = true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}")
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_STOP) {
            isRunning = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
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
