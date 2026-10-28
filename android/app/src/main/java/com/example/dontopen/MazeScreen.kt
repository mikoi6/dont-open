package com.example.dontopen

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun MazeScreen(app: AppEntry, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---- 进入时做一次快照：本次要穿几层 ----
    // 注意顺序：先读旧次数算层数，再 +1。这样"第一次进来 3 层，第二次 4 层"。
    val enteredCount = remember { Store.todayCount(ctx) }
    val total = remember { Maze.totalDepth(Store.baseDepth(ctx), enteredCount) }
    val keyword = remember { Store.keyword(ctx) }

    LaunchedEffect(Unit) { Store.bumpTodayCount(ctx) }

    // 系统返回键统一回首页，避免直接退出 App
    BackHandler(enabled = true) { onBack() }

    // ---- 状态机 ----
    var depth by remember { mutableIntStateOf(1) }     // 当前第几层（1-based）
    var retry by remember { mutableIntStateOf(0) }     // 本层点错几次（参与洗牌种子）
    var wrongIdx by remember { mutableIntStateOf(-1) } // 刚刚点错的格子，-1 表示没有
    var loading by remember { mutableStateOf(false) }   // 正在"展开"下一层
    var gate by remember { mutableStateOf(false) }      // 是否进入最后一关

    val cellCount = when {
        depth <= 2 -> 6
        depth <= 4 -> 9
        else -> 12
    }

    // 每层之间的种子不同 —— 位置不可记忆。retry 变化 = 重新洗牌。
    val slots = remember(depth, retry, cellCount, keyword) {
        Maze.buildLayer(
            keyword = keyword,
            count = cellCount,
            rnd = Random(depth * 977 + retry * 31 + app.pkg.hashCode())
        )
    }

    // ---- 最后一关 ----
    if (gate) {
        GateScreen(
            app = app,
            layersPassed = total,
            todayCount = enteredCount,
            onCancel = onBack,
            onOpen = {
                launchApp(ctx, app.pkg)
                onBack()
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // ---- 顶栏 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("←", color = TextDim, fontSize = 18.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "第 $depth / $total 层",
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            AppIcon(app.pkg, 26.dp)
            Spacer(Modifier.width(6.dp))
            Text(app.label, color = TextDim, fontSize = 12.sp, maxLines = 1)
        }

        Spacer(Modifier.height(12.dp))

        // ---- 层进度点 ----
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(total) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < depth) Accent else TextDim.copy(alpha = 0.2f))
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- 要找什么（这是全场最重要的提示，没它就成了纯猜谜）----
        Text("找到并进入", color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(keyword, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("这个名字的文件夹", color = TextDim, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))

        // ---- 文件夹网格 ----
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(slots.size) { i ->
                val slot = slots[i]
                FolderCell(
                    text = slot.text,
                    isWrong = wrongIdx == i,
                    enabled = !loading && wrongIdx < 0,
                    onClick = {
                        if (slot.isPath) {
                            loading = true
                            buzz(ctx, 12L)
                            scope.launch {
                                delay(700)          // 假装的"展开"耗时，制造仪式感
                                loading = false
                                if (depth >= total) {
                                    gate = true
                                } else {
                                    depth += 1
                                    retry = 0
                                    wrongIdx = -1
                                }
                            }
                        } else {
                            wrongIdx = i
                            buzz(ctx, 45L)
                            scope.launch {
                                delay(650)
                                wrongIdx = -1
                                retry += 1        // 触发洗牌
                            }
                        }
                    }
                )
            }
        }

        // ---- 底部提示 ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                loading -> Text("正在展开…", color = Accent, fontSize = 13.sp)
                wrongIdx >= 0 -> Text("这里没有你要找的东西", color = Danger, fontSize = 13.sp)
                else -> Text(
                    "点错会重新洗牌，位置是记不住的",
                    color = TextDim.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FolderCell(
    text: String,
    isWrong: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (isWrong) 0.92f else 1f, label = "scale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(if (isWrong) Danger.copy(alpha = 0.14f) else CardBg)
            .border(
                width = 1.dp,
                color = if (isWrong) Danger else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        // 文件夹形状用 Canvas 一次画完：小标签 + 主体在同一个坐标系里，不会错位
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1.18f)
        ) {
            val w = size.width
            val h = size.height
            val tabH = h * 0.18f
            val r = 7.dp.toPx()

            val bodyColor = if (isWrong) Danger.copy(alpha = 0.42f) else Accent.copy(alpha = 0.26f)
            val tabColor = if (isWrong) Danger.copy(alpha = 0.62f) else Accent.copy(alpha = 0.44f)

            // 顶部小标签（画高一点，下半部分被主体盖住，看起来就连成一体了）
            drawRoundRect(
                color = tabColor,
                topLeft = Offset(0f, 0f),
                size = Size(w * 0.46f, tabH * 2.4f),
                cornerRadius = CornerRadius(r, r)
            )
            // 主体
            drawRoundRect(
                color = bodyColor,
                topLeft = Offset(0f, tabH),
                size = Size(w, h - tabH),
                cornerRadius = CornerRadius(r, r)
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = text,
            color = if (isWrong) Danger else TextMain,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 最后一关。
 * 摩擦来自三件事叠加：
 *   1) 必须等冷静倒计时走完
 *   2) 必须按住 3 秒不松手
 *   3) 屏幕上写着你自己设的问题，你无法回避它
 */
@Composable
private fun GateScreen(
    app: AppEntry,
    layersPassed: Int,
    todayCount: Int,
    onCancel: () -> Unit,
    onOpen: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val progress = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }
    var left by remember { mutableIntStateOf(Store.calmSec(ctx)) }
    val question = remember { Store.reason(ctx) }

    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left -= 1
        }
    }

    LaunchedEffect(done) {
        if (done) {
            buzz(ctx, 30L)
            kotlinx.coroutines.delay(120)
            onOpen()
        }
    }

    val ready = left <= 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        AppIcon(app.pkg, 76.dp)
        Spacer(Modifier.height(20.dp))

        Text(
            "你确定要打开\n${app.label} 吗",
            color = TextMain,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "你已经穿过 $layersPassed 层，今天第 ${todayCount + 1} 次想开它",
            color = TextDim,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(36.dp))

        // 自省问题
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .padding(18.dp)
        ) {
            Text(
                question,
                color = TextMain.copy(alpha = 0.85f),
                fontSize = 15.sp,
                lineHeight = 23.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.weight(1f))

        // 按住 3 秒
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(31.dp))
                .background(if (ready) Accent else Accent.copy(alpha = 0.2f))
                .pointerInput(ready) {
                    if (!ready) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            val job = scope.launch {
                                progress.animateTo(1f, tween(3000, easing = LinearEasing))
                                done = true
                            }
                            tryAwaitRelease()
                            if (!done) {
                                job.cancel()
                                scope.launch { progress.animateTo(0f, tween(150)) }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // 进度填充条
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                    .background(Color.White.copy(alpha = 0.26f))
            )
            Text(
                when {
                    !ready -> "先安静 $left 秒"
                    progress.value > 0.05f -> "别松手…"
                    else -> "按住 3 秒，打开它"
                },
                color = if (ready) Color.White else TextDim,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("算了", color = TextDim, fontSize = 14.sp)
        }

        Spacer(Modifier.height(28.dp))
    }
}

private fun buzz(ctx: Context, ms: Long) {
    runCatching {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }
}
