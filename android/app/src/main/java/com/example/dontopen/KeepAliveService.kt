package com.example.dontopen

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

/**
 * 伴随无障碍服务的前台服务，作用只有一个：把进程留在前台组。
 *
 * MIUI / HyperOS 会激进回收后台进程，进程一死，无障碍服务就跟着没了，
 * 之后它的「安全中心」还会把无障碍权限一并撤销（就是你看到的「第二次点开就不拦了」）。
 * 一条低优先级常驻通知，能把被杀的几率压到最低。
 */
class KeepAliveService : Service() {

    companion object {
        private const val TAG = "KeepAliveService"
        private const val CHANNEL_ID = "guard_keepalive"
        private const val NOTIF_ID = 1

        fun start(ctx: Context) {
            val i = Intent(ctx, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, KeepAliveService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 保活是「锦上添花」：万一系统禁止前台服务（Android 12+ 后台限制），
        // 直接停掉自己即可，绝不能让异常连累无障碍服务。
        try {
            ensureChannel()
            val n = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "startForeground failed", t)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return b
            .setSmallIcon(R.drawable.ic_guard)
            .setContentTitle("桌面拦截运行中")
            .setContentText("守护受管控的应用，点开会被拦回迷宫")
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "桌面拦截",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "保持桌面拦截服务存活"
                    setShowBadge(false)
                }
            )
        }
    }
}
