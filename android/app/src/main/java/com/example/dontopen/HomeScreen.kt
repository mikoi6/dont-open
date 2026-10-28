package com.example.dontopen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import androidx.core.graphics.drawable.toBitmap

@Composable
fun HomeScreen(allApps: List<AppEntry>, onEnter: (AppEntry) -> Unit) {
    val ctx = LocalContext.current

    var guarded by remember { mutableStateOf(Store.guarded(ctx)) }
    var showPicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // 今日状态（进入迷宫后会变，所以每次回首页重新读）
    val todayCount = Store.todayCount(ctx)
    val planDepth = Maze.totalDepth(Store.baseDepth(ctx), todayCount)

    val mine = allApps.filter { it.pkg in guarded }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(24.dp)) }

        // ---------- 标题 ----------
        item {
            Text(
                text = "你确定要\n打开吗",
                color = TextMain,
                fontSize = 38.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "把挡不住的东西，塞进很深的地方。",
                color = TextDim,
                fontSize = 14.sp
            )
        }

        // ---------- 今日状态 ----------
        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("今天想开 $todayCount 次", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (todayCount == 0) "现在进入要穿 $planDepth 层"
                        else "想开得越多，门越厚 —— 这次要穿 $planDepth 层",
                        color = TextDim,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = "$planDepth",
                    color = Accent,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ---------- 受管应用 ----------
        item {
            Spacer(Modifier.height(8.dp))
            Text("受管控的应用", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        if (mine.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, TextDim.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("还没有藏任何东西\n点下面「＋ 把应用藏起来」", color = TextDim, fontSize = 13.sp)
                }
            }
        } else {
            items(mine, key = { it.pkg }) { app ->
                GuardedRow(app) { onEnter(app) }
            }
        }

        // ---------- 添加 ----------
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .clickable { showPicker = true }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("＋  把应用藏起来", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }

        // ---------- 设置 ----------
        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showSettings = !showSettings }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (showSettings) "▾" else "▸", color = TextDim, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Text("设置", color = TextDim, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "口令「${Store.keyword(ctx)}」· 基础 ${Store.baseDepth(ctx)} 层",
                    color = TextDim.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }

        if (showSettings) {
            item { SettingsPanel() }
        }

        // ---------- 诚实声明 ----------
        item {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "当前版本只管控「从这里进入」。\n从手机桌面直接点图标仍然可以绕过 —— 用无障碍服务拦截是下一版的事。",
                color = TextDim.copy(alpha = 0.6f),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showPicker) {
        AppPicker(
            all = allApps,
            selected = guarded,
            onDismiss = { showPicker = false },
            onToggle = { pkg ->
                guarded = if (pkg in guarded) guarded - pkg else guarded + pkg
                Store.setGuarded(ctx, guarded)
            }
        )
    }
}

@Composable
private fun GuardedRow(app: AppEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.pkg, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text("进入需要穿过多层文件夹", color = TextDim, fontSize = 12.sp)
        }
        Text("→", color = Accent, fontSize = 18.sp)
    }
}

@Composable
fun AppIcon(pkg: String, size: Dp) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) {
        runCatching {
            ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(CardBg)
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    pkg.takeLast(1).uppercase(),
                    color = TextDim,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsPanel() {
    val ctx = LocalContext.current

    var keyword by remember { mutableStateOf(Store.keyword(ctx)) }
    var depth by remember { mutableStateOf(Store.baseDepth(ctx)) }
    var calm by remember { mutableStateOf(Store.calmSec(ctx)) }
    var reason by remember { mutableStateOf(Store.reason(ctx)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("口令词", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(
            "每层里只有一个文件夹叫这个名字，其余都是形近的假货。找它，才能往下走。",
            color = TextDim,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        OutlinedTextField(
            value = keyword,
            onValueChange = {
                keyword = it
                if (it.isNotBlank()) Store.setKeyword(ctx, it.trim())
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = TextDim.copy(alpha = 0.4f)
            )
        )

        Stepper("基础层数", depth, 1, 8, "层") {
            depth = it
            Store.setBaseDepth(ctx, it)
        }

        Stepper("最后一关冷静", calm, 3, 30, "秒") {
            calm = it
            Store.setCalmSec(ctx, it)
        }

        Text("最后一关会问你", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = reason,
            onValueChange = {
                reason = it
                Store.setReason(ctx, it)
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = TextDim.copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    unit: String,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
        StepBtn("−") { if (value > min) onChange(value - 1) }
        Text(
            "$value$unit",
            color = TextMain,
            fontSize = 15.sp,
            modifier = Modifier
                .width(60.dp)
                .padding(horizontal = 8.dp)
        )
        StepBtn("+") { if (value < max) onChange(value + 1) }
    }
}

@Composable
private fun StepBtn(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Ink)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AppPicker(
    all: List<AppEntry>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit
) {
    var q by remember { mutableStateOf("") }

    val filtered = if (q.isBlank()) all
    else all.filter { it.label.contains(q, true) || it.pkg.contains(q, true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "选择要藏起来的应用",
                        color = TextMain,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("完成", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    placeholder = { Text("搜索应用", color = TextDim, fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = TextDim.copy(alpha = 0.4f)
                    )
                )
                Spacer(Modifier.height(8.dp))

                if (all.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("正在读取应用列表…", color = TextDim, fontSize = 13.sp)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.pkg }) { app ->
                        val on = app.pkg in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (on) Accent.copy(alpha = 0.12f) else CardBg)
                                .clickable { onToggle(app.pkg) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(app.pkg, 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                app.label,
                                color = TextMain,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(if (on) Accent else TextDim.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (on) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
