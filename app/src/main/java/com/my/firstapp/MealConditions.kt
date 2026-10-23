package com.my.firstapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A cancellable draft of decision rules, independent of unrelated application settings. */
data class MealConditions(val budget:Double?,val walk:Int?,val avoid:Int,val unknownPrice:Boolean,val unknownWalk:Boolean) {
    fun applyTo(s:Settings)=s.copy(budget=budget,maxWalkMinutes=walk,avoidDays=avoid,allowUnknownPrice=unknownPrice,allowUnknownWalk=unknownWalk)
    fun summary()=listOf(budget?.let{"菜价 ${it.yuan()} 内"}?:"预算不限",walk?.let{"步行 $it 分钟内"}?:"路程不限",if(avoid==0)"近期不限" else "避开 $avoid 天").joinToString(" · ")
    companion object { fun from(s:Settings)=MealConditions(s.budget,s.maxWalkMinutes,s.avoidDays,s.allowUnknownPrice,s.allowUnknownWalk) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealConditionsEditor(settings:Settings,onCancel:()->Unit,onApply:(MealConditions)->Unit) {
    var draft by rememberSaveable(stateSaver=listSaver<MealConditions,String>(
        save={listOf(it.budget?.toString().orEmpty(),it.walk?.toString().orEmpty(),it.avoid.toString(),it.unknownPrice.toString(),it.unknownWalk.toString())},
        restore={MealConditions(it[0].toDoubleOrNull(),it[1].toIntOrNull(),it[2].toInt(),it[3].toBoolean(),it[4].toBoolean())}
    )) { mutableStateOf(MealConditions.from(settings)) }
    var custom by rememberSaveable { mutableStateOf(settings.budget?.toString().orEmpty()) }
    var customEdited by rememberSaveable { mutableStateOf(false) }
    val valid = !customEdited || custom.isBlank() || custom.toDoubleOrNull()?.let { it.isFinite() && it>=0 }==true
    BackHandler(onBack=onCancel)
    Column(Modifier.fillMaxSize().background(AppColors.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onCancel){Text("取消")}
            Text("这一餐的条件",fontSize=23.sp,color=AppColors.Text,modifier=Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("菜品 / 套餐价格上限",fontSize=18.sp,color=AppColors.Text)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf<Double?>(null,15.0,20.0,30.0,50.0).forEach { price ->
                    Pill(price?.yuan()?:"不限",active=draft.budget==price && !customEdited){draft=draft.copy(budget=price);custom=price?.toString().orEmpty();customEdited=false}
                }
            }
            OutlinedTextField(value=custom,onValueChange={custom=it;customEdited=true},label={Text("自定义金额，留空不限")},singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=!valid,modifier=Modifier.fillMaxWidth())
            ConditionSwitch("允许价格待确认",draft.unknownPrice){draft=draft.copy(unknownPrice=it)}
            Text("步行时间上限",fontSize=18.sp,color=AppColors.Text)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf<Int?>(null,10,15,30).forEach { walk -> Pill(walk?.let{"$it 分钟"}?:"不限",active=draft.walk==walk){draft=draft.copy(walk=walk)} }
            }
            ConditionSwitch("允许路程未填写",draft.unknownWalk){draft=draft.copy(unknownWalk=it)}
            Text("路程是手动估计；价格以记录为准，抽中后仍需核对。",fontSize=13.sp,color=AppColors.SubText)
            Text("最近吃过的先避开",fontSize=18.sp,color=AppColors.Text)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(0,1,2,3,7).forEach { days -> Pill(if(days==0)"不限" else "$days 天",active=draft.avoid==days){draft=draft.copy(avoid=days)} }
            }
            Text("应用新条件后会重新挑选；取消会保留刚才的结果。",fontSize=13.sp,color=AppColors.SubText)
            Spacer(Modifier.height(12.dp))
        }
        Column(Modifier.background(AppColors.Surface).padding(16.dp)) {
            MealPrimaryAction("应用条件",{onApply(if(customEdited)draft.copy(budget=custom.trim().takeIf{it.isNotEmpty()}?.toDoubleOrNull()) else draft)},Modifier.fillMaxWidth(),enabled=valid)
        }
    }
}
@Composable private fun ConditionSwitch(label:String,checked:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(label,modifier=Modifier.weight(1f),color=AppColors.Text,fontSize=14.sp)
        Switch(checked=checked,onCheckedChange=onChange)
    }
}

@Composable
internal fun MealPrimaryAction(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true) {
    Button(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=54.dp),
        shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors=ButtonDefaults.buttonColors(containerColor=AppColors.Deep,contentColor=AppColors.OnPrimary)) {
        Text(text,fontSize=18.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}
