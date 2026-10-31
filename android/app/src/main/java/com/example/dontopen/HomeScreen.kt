package com.example.dontopen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun HomeScreen(
    allApps: List<AppEntry>,
    onEnter: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit
) {
    val ctx = LocalContext.current

    var guarded by remember { mutableStateOf(Store.guarded(ctx)) }
    var showPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AppEntry?>(null) }

    // 无障碍服务 / 悬浮窗开关都在系统设置里改，回来时要刷新一次状态
    var guardOn by remember { mutableStateOf(GuardService.isEnabled(ctx)) }
    var overlayOn by remember { mutableStateOf(GuardService.isOverlayEnabled(ctx)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                guardOn = GuardService.isEnabled(ctx)
                overlayOn = GuardService.isOverlayEnabled(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenSettings)
                        .padding(8.dp)
                ) {
                    Text("⚙", color = TextDim, fontSize = 22.sp)
                }
            }
        }

        // ---------- 桌面拦截状态 ----------
        item {
            Spacer(Modifier.height(8.dp))
            GuardCard(
                enabled = guardOn,
                overlayOn = overlayOn,
                onEnableAccessibility = {
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                onEnableOverlay = {
                    runCatching {
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${ctx.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
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
                    .clickable(onClick = onOpenStats)
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
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$planDepth",
                        color = Accent,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("查看历史 →", color = TextDim, fontSize = 11.sp)
                }
            }
        }

        // ---------- 受管应用 ----------
        item {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("受管控的应用", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (mine.isNotEmpty()) {
                    Text(
                        text = if (editing) "完成" else "编辑",
                        color = Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { editing = !editing }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
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
                GuardedRow(
                    app = app,
                    editing = editing,
                    onClick = { onEnter(app) },
                    onDelete = { pendingDelete = app }
                )
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

        // ---------- 诚实声明 ----------
        item {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "拦截开启后，会有一条常驻通知用来保活（防止系统回收）。\n它只检测前台窗口变化，不读取、不上传任何内容。",
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

    // 删除确认弹窗
    pendingDelete?.let { app ->
        Dialog(onDismissRequest = { pendingDelete = null }) {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text("移除「${app.label}」？", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("只是从受管控列表里删掉，应用本身不会卸载。之后想再加回来随时可以。", color = TextDim, fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardBg)
                                .border(1.dp, TextDim.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .clickable { pendingDelete = null }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("算了", color = TextDim, fontSize = 14.sp)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Danger)
                                .clickable {
                                    guarded = guarded - app.pkg
                                    Store.setGuarded(ctx, guarded)
                                    if (Store.unlockedPkg(ctx) == app.pkg) Store.setUnlockedPkg(ctx, null)
                                    if (guarded.isEmpty()) editing = false
                                    pendingDelete = null
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("移除", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuardCard(
    enabled: Boolean,
    overlayOn: Boolean,
    onEnableAccessibility: () -> Unit,
    onEnableOverlay: () -> Unit
) {
    val ready = enabled && overlayOn
    val on = if (ready) OkGreen else Danger

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(on.copy(alpha = 0.10f))
            .border(1.dp, on.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(on)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (ready) "桌面拦截已就绪" else "桌面拦截未就绪",
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (ready) "从手机桌面直接点开受管应用，也会被拉回迷宫。"
            else "开启下面两项后，从桌面点开受管应用也会被拦回迷宫。",
            color = TextDim,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        if (!enabled) {
            Spacer(Modifier.height(14.dp))
            PrereqRow(ok = false, label = "无障碍服务", action = "去开启", onClick = onEnableAccessibility)
        } else {
            Spacer(Modifier.height(14.dp))
            PrereqRow(ok = true, label = "无障碍服务", action = "已开启", onClick = null)
        }

        Spacer(Modifier.height(8.dp))
        PrereqRow(ok = overlayOn, label = "显示在其他应用上层", action = if (overlayOn) "已授权" else "去授权", onClick = if (overlayOn) null else onEnableOverlay)

        if (!ready) {
            Spacer(Modifier.height(14.dp))
            HyperOsChecklist()
        }
    }
}

@Composable
private fun HyperOsChecklist() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ink.copy(alpha = 0.6f))
            .padding(14.dp)
    ) {
        Text("MIUI / HyperOS 额外要做的", color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "这些 ROM 会反复关闭无障碍服务，开完上面两项后，还要在系统里逐个确认：",
            color = TextDim,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(8.dp))
        TipLine("自启动", "设为「允许」")
        TipLine("后台弹出界面", "设为「允许」")
        TipLine("省电策略", "设为「无限制」")
        TipLine("最近任务", "长按本应用 → 锁定")
    }
}

@Composable
private fun TipLine(k: String, v: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(k, color = TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(v, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PrereqRow(ok: Boolean, label: String, action: String, onClick: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (ok) OkGreen else TextDim.copy(alpha = 0.4f))
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = TextMain, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (onClick != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Accent)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(action, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        } else {
            Text(action, color = if (ok) OkGreen else TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun GuardedRow(app: AppEntry, editing: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
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
            Text(
                if (editing) "点击右侧按钮移除管控" else "进入需要穿过多层文件夹",
                color = TextDim,
                fontSize = 12.sp
            )
        }
        if (editing) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Danger.copy(alpha = 0.15f))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text("删除", color = Danger, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        } else {
            Text("→", color = Accent, fontSize = 18.sp)
        }
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
