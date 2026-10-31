package com.example.dontopen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    themeMode: String,
    onThemeChange: (String) -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current

    var keyword by remember { mutableStateOf(Store.keyword(ctx)) }
    var depth by remember { mutableStateOf(Store.baseDepth(ctx)) }
    var calm by remember { mutableStateOf(Store.calmSec(ctx)) }
    var reason by remember { mutableStateOf(Store.reason(ctx)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
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
            Text("设置", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))

        // ---- 主题 ----
        Text("主题", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text("黑 / 白配色，切换立即生效，并会记住你的选择。", color = TextDim, fontSize = 11.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeOption("黑", selected = themeMode != "light") { onThemeChange("dark") }
            ThemeOption("白", selected = themeMode == "light") { onThemeChange("light") }
        }

        Spacer(Modifier.height(28.dp))

        // ---- 口令词 ----
        Text("口令词", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(
            "每层里只有一个文件夹叫这个名字，其余都是形近的假货。找它，才能往下走。",
            color = TextDim,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(8.dp))
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

        Spacer(Modifier.height(20.dp))

        Stepper("基础层数", depth, 1, 8, "层") {
            depth = it
            Store.setBaseDepth(ctx, it)
        }

        Spacer(Modifier.height(14.dp))

        Stepper("最后一关冷静", calm, 3, 30, "秒") {
            calm = it
            Store.setCalmSec(ctx, it)
        }

        Spacer(Modifier.height(24.dp))

        Text("最后一关会问你", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
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

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun RowScope.ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Accent else CardBg)
            .border(
                1.dp,
                if (selected) Accent else TextDim.copy(alpha = 0.25f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
            .background(CardBg)
            .border(1.dp, TextDim.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
