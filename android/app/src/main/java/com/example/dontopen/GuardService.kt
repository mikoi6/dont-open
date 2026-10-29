package com.example.dontopen

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout

/**
 * v2 的核心：桌面拦截。
 *
 * 用无障碍服务监听前台窗口变化，一旦发现受管控的应用被调到前台，
 * 先用一层纯色遮罩盖住它（避免"闪一下"再进迷宫的生硬体验），
 * 再把用户拉回迷宫。
 *
 * 放行策略（这是最容易被写错的地方）：
 *  - 每次成功穿过迷宫，就"解锁"这个包名，本次会话放行，避免一启动又被拦回来形成死循环。
 *  - 一旦前台切到别的应用 / 回到桌面，锁自动重新挂上，下次再打开还得穿迷宫。
 *  - 一次只放行一个包名：从 A 切到受管应用 B 时，A 的放行立即作废。
 */
class GuardService : AccessibilityService() {

    companion object {
        /** MainActivity 读取这个 extra，直接跳到对应应用的迷宫 */
        const val EXTRA_PKG = "extra_pkg"

        private const val TAG = "GuardService"

        /** 遮罩落位到启动 Activity 之间的缓冲时间 */
        private const val OVERLAY_SETTLE_MS = 150L

        // 这些包的窗口变化不算"离开应用"：音量弹窗、系统 UI、输入法都不该触发重新上锁。
        private val IGNORED = setOf(
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.sohu.inputmethod.sogou",
            "com.baidu.input",
            "com.iflytek.inputmethod"
        )

        /** 无障碍服务是否已开启（首页用来显示状态） */
        fun isEnabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            // 用 ComponentName 解析，兼容厂商可能存的「短格式 / 大小写」差异
            val want = ComponentName(ctx, GuardService::class.java)
            return flat.split(':').any { raw ->
                runCatching { ComponentName.unflattenFromString(raw) == want }.getOrDefault(false)
            }
        }

        /** 悬浮窗（显示在其他应用上层）权限是否已授权 */
        fun isOverlayEnabled(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)
    }

    private var overlay: FrameLayout? = null
    private var lastPkg: String? = null
    private val homePkgs by lazy { homePackages() }
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = null
        lastPkg = null
        // 拉起伴随的前台服务保活，防止 HyperOS 回收进程后连带禁用无障碍
        runCatching { KeepAliveService.start(this) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 防崩壳：MIUI 有「服务崩一次就直接禁用」的机制，任何异常都不能让它漏出去
        try {
            handleEvent(event)
        } catch (t: Throwable) {
            Log.w(TAG, "handleEvent error", t)
        }
    }

    private fun handleEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()?.takeIf { it.isNotEmpty() } ?: return
        if (pkg in IGNORED) return

        // 我们自己在前台（用户正在走迷宫）：撤遮罩即可，不做状态变更
        if (pkg == packageName) {
            lastPkg = pkg
            clearOverlay()
            return
        }

        // 前台包名没变（同一应用内切换窗口），忽略
        if (pkg == lastPkg) return
        lastPkg = pkg

        val guarded = Store.guarded(this)
        val unlocked = Store.unlockedPkg(this)

        when {
            pkg in guarded -> {
                if (pkg == unlocked) return     // 本次会话已放行
                intercept(pkg)
            }
            pkg in homePkgs -> {
                if (unlocked != null) Store.setUnlockedPkg(this, null) // 回桌面 = 重新上锁
                clearOverlay()
            }
        }
    }

    private fun intercept(pkg: String) {
        Store.setUnlockedPkg(this, null)    // 换目标时，之前的放行作废
        showOverlay()                       // 先盖住目标应用，避免"闪一下"

        // 关键：延迟一小段再启动 Activity。
        // 1) 让遮罩先变成"可见窗口"，这样拿到悬浮窗权限后，Android 10+ 的后台启动限制（BAL）
        //    才会豁免，MainActivity 才能真正抢到前台（否则只会被丢在后台）。
        // 2) 某些国产 ROM 对"后台拉起前台"有额外限制，这里也顺带缓冲一瞬。
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(EXTRA_PKG, pkg)
        }
        handler.postDelayed({
            runCatching { startActivity(intent) }
                .onFailure { Log.w(TAG, "startActivity failed: ${it.message}") }
        }, OVERLAY_SETTLE_MS)
    }

    private fun showOverlay() {
        if (overlay != null) return
        val v = FrameLayout(this).apply { setBackgroundColor(0xFF0E1116.toInt()) }
        // 有悬浮窗权限用 TYPE_APPLICATION_OVERLAY（同时享受 BAL 豁免）；没有则退回无障碍遮罩。
        val type = if (Settings.canDrawOverlays(this)) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply { gravity = Gravity.TOP or Gravity.START }
        runCatching {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(v, lp)
            overlay = v
        }.onFailure { Log.w(TAG, "addOverlay failed: ${it.message}") }
    }

    private fun clearOverlay() {
        val v = overlay ?: return
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
        overlay = null
    }

    private fun homePackages(): Set<String> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return runCatching {
            packageManager.queryIntentActivities(i, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        runCatching { KeepAliveService.stop(this) }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clearOverlay()
        runCatching { KeepAliveService.stop(this) }
        super.onDestroy()
    }
}
