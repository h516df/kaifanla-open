package com.my.firstapp

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun avoidLabel(days: Int) = when (days) {
    0 -> "吃过的也能抽"
    1 -> "今天吃过的不抽"
    7 -> "一周内吃过的不抽"
    else -> "${days}天内吃过的不抽"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RulesBar(vm: AppViewModel, onClick: () -> Unit) {
    val skip = vm.skip
    val skipCount = skip.shopIds.size + skip.dishIds.size
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val budget = vm.settings.budget
        Pill(if (budget == null) "💰 预算不限" else "💰 ${budget.yuan()} 以内", active = budget != null, onClick = onClick)
        Pill("🙅 ${avoidLabel(vm.settings.avoidDays)}", active = vm.settings.avoidDays > 0, onClick = onClick)
        if (skipCount > 0) Pill("🚫 今天不想吃 $skipCount", active = true, onClick = onClick)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RulesPreferencesScreen(vm: AppViewModel, onDismiss: () -> Unit, direct: Boolean = true, onModeChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    fun report(saved: Boolean) {
        if (!saved) Toast.makeText(context, "没保存成功，请再试一次", Toast.LENGTH_SHORT).show()
    }
    val settings = vm.settings
    var custom by remember { mutableStateOf("") }
    androidx.activity.compose.BackHandler(onBack=onDismiss)
    ScreenColumn {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("这一餐的偏好",fontFamily=CuteFont,fontSize=24.sp,color=AppColors.Text,modifier=Modifier.weight(1f))
            TextButton(onClick=onDismiss) { Text("好啦") }
        }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("💰 已录入菜品 / 套餐价上限", fontWeight = FontWeight.Bold, color = AppColors.Text)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<Double?>(null, 15.0, 20.0, 30.0, 50.0, 80.0).forEach { b ->
                        Pill(b?.let { "${it.yuan()} 以内" } ?: "不限", active = settings.budget == b) {
                            report(vm.updateSettings { it.copy(budget = b) })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it.trim() },
                        placeholder = { Text("自定义，比如 25") },
                        singleLine = true,
                        shape = CircleShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = custom.toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 } == true,
                        onClick = { report(vm.updateSettings { it.copy(budget = custom.toDoubleOrNull()) }) },
                    ) { Text("用这个") }
                }

                Text("团购价需核对适用条件，不代表人均或整桌费用。", color=AppColors.SubText, fontSize=14.sp)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("有预算时，允许价格待确认", Modifier.weight(1f), color=AppColors.Text)
                    androidx.compose.material3.Switch(checked=settings.allowUnknownPrice, enabled=settings.budget!=null,
                        onCheckedChange={ value -> report(vm.updateSettings { it.copy(allowUnknownPrice=value) }) })
                }
                Text("🚶 可接受的步行时间", fontWeight=FontWeight.Bold,color=AppColors.Text)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf<Int?>(null,10,15,30).forEach { minutes ->
                        Pill(minutes?.let { "$it 分钟内" } ?: "不限",active=settings.maxWalkMinutes==minutes) {
                            report(vm.updateSettings { it.copy(maxWalkMinutes=minutes) })
                        }
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("限时步行时，允许路程未填写",Modifier.weight(1f),color=AppColors.Text)
                    androidx.compose.material3.Switch(checked=settings.allowUnknownWalk, enabled=settings.maxWalkMinutes!=null,
                        onCheckedChange={ value -> report(vm.updateSettings { it.copy(allowUnknownWalk=value) }) })
                }
                Text("步行时间来自你在店铺里选填的估计，并非实时路线；未知会明示，不必填完所有店。",color=AppColors.SubText,fontSize=14.sp)
                Text("🙅 最近吃过的先不抽", fontWeight = FontWeight.Bold, color = AppColors.Text, modifier = Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "不限制", 1 to "今天", 2 to "2天内", 3 to "3天内", 7 to "一周内").forEach { (d, label) ->
                        Pill(label, active = settings.avoidDays == d) { report(vm.updateSettings { it.copy(avoidDays = d) }) }
                    }
                }

                Text("高级：选择方式",fontWeight=FontWeight.Bold,color=AppColors.Text)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Pill("一次选一餐",active=direct){onModeChange(true)}
                    Pill("先挑店再选菜",active=!direct){onModeChange(false)}
                }
                val skip = vm.skip
                if (skip.shopIds.isNotEmpty() || skip.dishIds.isNotEmpty()) {
                    Text("🚫 今天不想吃的（明天自动恢复）", fontWeight = FontWeight.Bold, color = AppColors.Text, modifier = Modifier.padding(top = 6.dp))
                    skip.shopIds.forEach { id ->
                        val s = vm.shops.find { it.id == id } ?: return@forEach
                        SkipRow("${s.emoji} ${s.name}（整家店）") { report(vm.unskipShop(id)) }
                    }
                    skip.dishIds.forEach { id ->
                        val s = vm.shops.find { shop -> shop.dishes.any { it.id == id } } ?: return@forEach
                        val d = s.dishes.first { it.id == id }
                        SkipRow("${s.emoji} ${s.name} · ${d.name}") { report(vm.unskipDish(id)) }
                    }
                    TextButton(onClick = { report(vm.clearSkip()) }) { Text("全部恢复") }
                }
            }
    }
}

@Composable
private fun SkipRow(text: String, onRestore: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 14.sp, color = AppColors.Text, modifier = Modifier.weight(1f))
        TextButton(onClick = onRestore) { Text("恢复") }
    }
}
