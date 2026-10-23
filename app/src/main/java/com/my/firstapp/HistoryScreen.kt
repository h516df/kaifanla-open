package com.my.firstapp

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val zone = TimeZone.getDefault()
    var deleting by remember { mutableStateOf<Record?>(null) }
    var clearing by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("认真吃饭的日子", fontSize = 28.sp, fontFamily = CuteFont, color = AppColors.Text)
                Text("每一顿小满足，都值得记住", fontSize = 13.sp, color = AppColors.SubText,
                    modifier = Modifier.padding(top = 6.dp))
            }
            if (vm.history.isNotEmpty()) Box {
                TextButton(onClick = { showMenu = true }) { Text("更多") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("清空所有记录") }, onClick = {
                        showMenu = false
                        clearing = true
                    })
                }
            }
        }
        MonthStats(vm.history)

        if (vm.history.isEmpty()) {
            EmptyCard("📒", "还没有记录哦", "转盘抽中后点「就吃这个」就会记在这里～")
            return@ScreenColumn
        }

        val dayFormat = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA).apply { timeZone = zone }
        val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA).apply { timeZone = zone }
        val days = remember(vm.history, zone.id) { mealDays(vm.history, zone) }
        days.forEach { (_, records) ->
            val day = dayFormat.format(Date(records.first().time))
            Text(day, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.SubText, modifier = Modifier.fillMaxWidth())
            records.forEach { r ->
                CuteCard(onClick = { deleting = r }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiBadge(r.shopEmoji, size = 44.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(r.dishName, fontSize = 18.sp, fontFamily = CuteFont, color = AppColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${r.shopName} · ${timeFormat.format(Date(r.time))}", fontSize = 13.sp, color = AppColors.SubText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if(r.price==null && r.groupPrice==null)"价格待确认" else r.paid.yuan(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.Primary)
                            if (r.saved > 0 && r.price != null) {
                                Text(
                                    r.price.yuan(),
                                    fontSize = 12.sp,
                                    color = AppColors.SubText,
                                    textDecoration = TextDecoration.LineThrough,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这条记录？") },
            text = { Text("${r.shopName} · ${r.dishName}") },
            confirmButton = {
                TextButton(onClick = {
                    if (vm.deleteRecord(r)) deleting = null
                    else Toast.makeText(context, "没保存成功，请再试一次", Toast.LENGTH_SHORT).show()
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("算了") } },
            containerColor = AppColors.Surface,
        )
    }
    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text("清空所有记录？") },
            text = { Text("清空后找不回来哦。") },
            confirmButton = {
                TextButton(onClick = {
                    if (vm.clearHistory()) clearing = false
                    else Toast.makeText(context, "没保存成功，请再试一次", Toast.LENGTH_SHORT).show()
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { clearing = false }) { Text("算了") } },
            containerColor = AppColors.Surface,
        )
    }
}

@Composable
private fun MonthStats(history: List<Record>) {
    val zone = TimeZone.getDefault()
    val now = Calendar.getInstance(zone)
    val summary = remember(history, now.get(Calendar.YEAR), now.get(Calendar.MONTH), zone.id) {
        mealMonth(history, now.timeInMillis, zone)
    }
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .clip(shape)
            .background(AppColors.Light)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${summary.year} / ${summary.month.toString().padStart(2, '0')}  ·  本月的小满足", fontSize = 14.sp, color = AppColors.SubText)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("${summary.count}", "顿饭")
            Stat(summary.paid.yuan(), "已记金额")
            Stat(summary.saved.yuan(), "团购省了")
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 26.sp, fontFamily = CuteFont, color = AppColors.Deep)
        Text(label, fontSize = 13.sp, color = AppColors.SubText)
    }
}
