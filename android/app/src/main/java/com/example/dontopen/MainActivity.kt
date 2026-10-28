package com.example.dontopen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---- 配色 ----
val Ink = Color(0xFF0E1116)          // 底色
val CardBg = Color(0xFF181D25)       // 卡片
val Accent = Color(0xFFE8632A)       // 强调（暖橙，像警示灯）
val TextMain = Color(0xFFF2F4F7)
val TextDim = Color(0xFF8A93A0)
val Danger = Color(0xFFE24A4A)
val OkGreen = Color(0xFF35B37E)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Ink,
    onBackground = TextMain,
    surface = CardBg,
    onSurface = TextMain,
    surfaceVariant = CardBg,
    onSurfaceVariant = TextDim,
    error = Danger
)

@Composable
fun DontOpenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

/** 只有两个页面，用不着 Navigation 库，一个状态变量就够了 */
sealed interface Screen {
    data object Home : Screen
    data class Maze(val app: AppEntry) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DontOpenTheme {
                Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
                    Root()
                }
            }
        }
    }
}

@Composable
fun Root() {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    // 应用列表只在启动时读一次 —— 放在这里而不是 HomeScreen 里，
    // 否则每次从迷宫返回首页都要重新扫一遍包，会闪一下空白。
    var allApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) { loadInstalledApps(ctx) }
    }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            allApps = allApps,
            onEnter = { screen = Screen.Maze(it) }
        )

        is Screen.Maze -> MazeScreen(
            app = s.app,
            onBack = { screen = Screen.Home }
        )
    }
}
