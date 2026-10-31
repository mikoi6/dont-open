package com.example.dontopen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current

    val stats = remember { Store.dailyStats(ctx) }
    var range by remember { mutableIntStateOf(7) }

    val today = remember { LocalDate.now() }
    val days = (0 until range).map { today.minusDays((range - 1 - it).toLong()) }
    val values = days.map { stats[it.toString()] ?: 0 }
    val total = values.sum()
    val maxV = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val labelFmt = DateTimeFormatter.ofPattern("M/d")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp)
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
            Text("每日统计", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(20.dp))

        // ---- 概览 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("最近 $range 天", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text("累计想打开 $total 次", color = TextDim, fontSize = 12.sp)
            }
            // 7 / 30 切换
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.copy(alpha = 0.5f))
                    .padding(3.dp)
            ) {
                RangeBtn("7 天", range == 7) { range = 7 }
                RangeBtn("30 天", range == 30) { range = 30 }
            }
        }

        Spacer(Modifier.height(28.dp))

        if (total == 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有记录", color = TextDim, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("从今天第一次进入迷宫开始，这里会逐渐长出柱子。", color = TextDim.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        } else {
            // ---- 柱状图 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEachIndexed { i, day ->
                    val v = values[i]
                    val barH = if (v == 0) 3.dp else 150.dp * (v.toFloat() / maxV)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (v > 0) {
                            Text("$v", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(3.dp))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.62f)
                                .height(barH)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (v > 0) Accent else TextDim.copy(alpha = 0.18f))
                        )
                        Spacer(Modifier.height(6.dp))
                        if (range == 7 || i % 5 == 0 || i == days.lastIndex) {
                            Text(day.format(labelFmt), color = TextDim, fontSize = 9.sp)
                        } else {
                            Spacer(Modifier.height(11.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RangeBtn(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else TextDim,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
