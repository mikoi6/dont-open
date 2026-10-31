package com.example.dontopen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---- 调色板 ----
data class AppColors(
    val ink: Color,        // 底色
    val cardBg: Color,     // 卡片
    val accent: Color,     // 强调（暖橙，像警示灯）
    val textMain: Color,
    val textDim: Color,
    val danger: Color,
    val okGreen: Color
)

val DarkColors = AppColors(
    ink = Color(0xFF0E1116),
    cardBg = Color(0xFF181D25),
    accent = Color(0xFFE8632A),
    textMain = Color(0xFFF2F4F7),
    textDim = Color(0xFF8A93A0),
    danger = Color(0xFFE24A4A),
    okGreen = Color(0xFF35B37E)
)

val LightColors = AppColors(
    ink = Color(0xFFF6F7F9),
    cardBg = Color(0xFFFFFFFF),
    accent = Color(0xFFE8632A),
    textMain = Color(0xFF1A1D24),
    textDim = Color(0xFF6B7280),
    danger = Color(0xFFE24A4A),
    okGreen = Color(0xFF2E9E6B)
)

val LocalAppColors = staticCompositionLocalOf { DarkColors }

// 主题化颜色：所有屏幕里的 Ink/CardBg/... 都从这里取，切换主题即时生效。
// 用 @Composable getter 是为了让现有代码里的引用几乎不用改。
val Ink: Color @Composable get() = LocalAppColors.current.ink
val CardBg: Color @Composable get() = LocalAppColors.current.cardBg
val Accent: Color @Composable get() = LocalAppColors.current.accent
val TextMain: Color @Composable get() = LocalAppColors.current.textMain
val TextDim: Color @Composable get() = LocalAppColors.current.textDim
val Danger: Color @Composable get() = LocalAppColors.current.danger
val OkGreen: Color @Composable get() = LocalAppColors.current.okGreen

@Composable
fun DontOpenTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) darkColorScheme(
        primary = colors.accent,
        onPrimary = Color.White,
        background = colors.ink,
        onBackground = colors.textMain,
        surface = colors.cardBg,
        onSurface = colors.textMain,
        surfaceVariant = colors.cardBg,
        onSurfaceVariant = colors.textDim,
        error = colors.danger
    ) else lightColorScheme(
        primary = colors.accent,
        onPrimary = Color.White,
        background = colors.ink,
        onBackground = colors.textMain,
        surface = colors.cardBg,
        onSurface = colors.textMain,
        surfaceVariant = colors.cardBg,
        onSurfaceVariant = colors.textDim,
        error = colors.danger
    )
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** 四个页面，用不着 Navigation 库，一个状态变量就够了 */
sealed interface Screen {
    data object Home : Screen
    data class Maze(val app: AppEntry) : Screen
    data object Settings : Screen
    data object Stats : Screen
}

class MainActivity : ComponentActivity() {

    // 被无障碍服务拦回来的目标应用（非 null 时直接进迷宫）
    private val intercepted = mutableStateOf<AppEntry?>(null)
    // 延迟到 onCreate（Context 挂好）再读 SharedPreferences，字段初始化阶段读会 NPE
    private val themeMode by lazy { mutableStateOf(Store.themeMode(this)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeFromIntent(intent)
        applySystemBars()
        setContent {
            DontOpenTheme(dark = themeMode.value != "light") {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val target = intercepted.value
                    Root(
                        interceptedApp = target,
                        themeMode = themeMode.value,
                        onThemeChange = { mode ->
                            themeMode.value = mode
                            Store.setThemeMode(this, mode)
                            applySystemBars()
                        },
                        onInterceptedDone = { intercepted.value = null }
                    )
                }
            }
        }
    }

    // singleTask + 拦截再进：已在前台/后台时，复用同一个实例走 onNewIntent
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeFromIntent(intent)
    }

    private fun routeFromIntent(intent: Intent?) {
        val pkg = intent?.getStringExtra(GuardService.EXTRA_PKG) ?: return
        val app = resolveApp(this, pkg) ?: return
        intercepted.value = app
    }

    // 状态栏/导航栏底色与图标跟着主题走（浅色主题要用深色图标）
    private fun applySystemBars() {
        val light = themeMode.value == "light"
        val colors = if (light) LightColors else DarkColors
        window.statusBarColor = colors.ink.toArgb()
        window.navigationBarColor = colors.ink.toArgb()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}

@Composable
fun Root(
    interceptedApp: AppEntry?,
    themeMode: String,
    onThemeChange: (String) -> Unit,
    onInterceptedDone: () -> Unit
) {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    // 应用列表只在启动时读一次 —— 放在这里而不是 HomeScreen 里，
    // 否则每次从迷宫返回首页都要重新扫一遍包，会闪一下空白。
    var allApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) { loadInstalledApps(ctx) }
    }

    // 被拦回时，直接跳进对应应用的迷宫
    LaunchedEffect(interceptedApp) {
        if (interceptedApp != null) screen = Screen.Maze(interceptedApp)
    }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            allApps = allApps,
            onEnter = { screen = Screen.Maze(it) },
            onOpenSettings = { screen = Screen.Settings },
            onOpenStats = { screen = Screen.Stats }
        )

        is Screen.Maze -> MazeScreen(
            app = s.app,
            onBack = {
                screen = Screen.Home
                if (s.app == interceptedApp) onInterceptedDone()
            }
        )

        Screen.Settings -> SettingsScreen(
            themeMode = themeMode,
            onThemeChange = onThemeChange,
            onBack = { screen = Screen.Home }
        )

        Screen.Stats -> StatsScreen(
            onBack = { screen = Screen.Home }
        )
    }
}
