package com.my.firstapp

import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** One screen = one decision. The action dock never scrolls away with the content. */
@Composable
fun SpinScreen(vm: AppViewModel, onGoShops: () -> Unit) {
    val context = LocalContext.current
    val compact = LocalConfiguration.current.screenHeightDp<740
    val scope = rememberCoroutineScope()
    val sound = LocalSound.current
    val mealSaver = listSaver<DecisionResult.Meal?,Long>(
        save={it?.let { meal -> listOf(meal.shop.id,meal.dish.id) } ?: emptyList()},
        restore={ids -> if(ids.size!=2)null else vm.shops.find{it.id==ids[0]}?.let { shop ->
            shop.dishes.find{it.id==ids[1] && vm.blockReason(shop,it)==null}?.let { DecisionResult.Meal(shop,it) }
        }})
    var result by rememberSaveable(stateSaver=mealSaver) { mutableStateOf<DecisionResult.Meal?>(null) }
    var confirmed by rememberSaveable(stateSaver=mealSaver) { mutableStateOf<DecisionResult.Meal?>(null) }
    var spinning by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var candidatesOpen by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<String?>(null) }
    var seen by rememberSaveable(stateSaver=listSaver<Set<Pair<Long,Long>>,Long>(
        save={it.flatMap { pair -> listOf(pair.first,pair.second) }},
        restore={it.chunked(2).mapNotNull { pair -> if(pair.size==2)pair[0] to pair[1] else null }.toSet()}
    )) { mutableStateOf<Set<Pair<Long,Long>>>(emptySet()) }
    val position = remember { Animatable(0f) }
    var frozen by remember { mutableStateOf<List<MealReelItem>?>(null) }
    val meals = vm.shops.flatMap { s -> vm.eligibleDishes(s).map { s to it } }
    val items = meals.map { (s,d) -> MealReelItem(d.name,s.name,s.emoji,d.paid?.yuan() ?: "价格待确认") }
    fun reportFailure() { Toast.makeText(context,"没保存成功，请再试一次",Toast.LENGTH_SHORT).show() }
    fun spin() {
        if (spinning || meals.isEmpty()) return
        val snapshot = meals.toList()
        val available = snapshot.indices.filter { (snapshot[it].first.id to snapshot[it].second.id) !in seen }
        val pool = available.ifEmpty { snapshot.indices.toList() }
        val chosen = pool.random()
        val restarting = available.isEmpty()
        seen = (if(restarting) emptySet() else seen) + (snapshot[chosen].first.id to snapshot[chosen].second.id)
        frozen = items.toList()
        result = null; saved = null; notice = if(restarting) "符合条件的都看过了，开始新一轮" else null; spinning = true
        if(vm.settings.sound) sound?.pop()
        scope.launch {
            try {
                val target = reelTarget(position.value,snapshot.size,chosen)
                val animate = AndroidSettings.Global.getFloat(context.contentResolver,AndroidSettings.Global.ANIMATOR_DURATION_SCALE,1f)>0f
                if (animate && snapshot.size>1) position.animateTo(target,tween(1450,easing=CubicBezierEasing(.12f,.75f,.2f,1f)))
                else position.snapTo(target)
                val (s,d) = snapshot[chosen]
                result = DecisionResult.Meal(s,d)
                if(vm.settings.sound) sound?.ding()
            } finally { spinning = false }
        }
    }
    fun confirm() {
        val chosen = confirmed ?: return
        val s = vm.shops.find { it.id == chosen.shop.id }
        val d = s?.dishes?.find { it.id == chosen.dish.id }
        if(s == null || d == null || d != chosen.dish || vm.blockReason(s,d)!=null) {
            result=null; confirmed=null; notice="菜单或条件已变化，请重新抽选"; return
        }
        if(vm.addRecord(s,d)) { saved=d.name; confirmed=null; frozen=null }
        else reportFailure()
    }
    if(editing) {
        MealConditionsEditor(vm.settings,onCancel={editing=false},onApply={draft ->
            val changed = draft != MealConditions.from(vm.settings)
            if(!changed || vm.updateSettings { draft.applyTo(it) }) {
                editing=false
                if(changed) { result=null; confirmed=null; frozen=null; saved=null; seen=emptySet(); notice="条件已更新，按新条件重新挑一餐" }
            } else reportFailure()
        })
        return
    }
    BackHandler(result!=null || confirmed!=null || candidatesOpen) {
        if(candidatesOpen)candidatesOpen=false else if(confirmed!=null){result=confirmed;confirmed=null} else result=null
    }
    if(candidatesOpen) {
        AlertDialog(onDismissRequest={candidatesOpen=false},containerColor=AppColors.Surface,
            title={Text("符合条件的 ${meals.size} 餐")},
            text={Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState())) {
                meals.forEach { (s,d) -> Text("${s.emoji} ${d.name}\n${s.name} · ${d.paid?.yuan() ?: "价格待确认"}",modifier=Modifier.padding(vertical=8.dp),color=AppColors.Text) }
            }},confirmButton={TextButton(onClick={candidatesOpen=false}){Text("返回")}})
    }
    Column(Modifier.fillMaxSize().background(AppColors.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("今天吃什么",fontSize=25.sp,fontWeight=FontWeight.SemiBold,color=AppColors.Text)
                if(!compact && result==null && confirmed==null && saved==null) Text("按你的条件，交给一点好运",fontSize=13.sp,color=AppColors.SubText)
            }

        }
        if(result==null && confirmed==null && saved==null) Text(MealConditions.from(vm.settings).summary(),fontSize=13.sp,color=AppColors.SubText,
            modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=8.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            notice?.let { Text(it,fontSize=14.sp,color=AppColors.Deep,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite }) }
            when {
                saved!=null -> {
                    Spacer(Modifier.height(24.dp))
                    Text("这一餐，记好了",fontSize=27.sp,color=AppColors.Text,fontWeight=FontWeight.SemiBold)
                    Text("${saved}已记入小本本",fontSize=16.sp,color=AppColors.SubText)
                    Text("用餐记录已保存，可以在小本本里查看。",fontSize=14.sp,color=AppColors.SubText)
                }
                confirmed!=null -> {
                    Text("这餐就定它",fontSize=24.sp,fontWeight=FontWeight.SemiBold,color=AppColors.Text)
                    MealDecisionTicket(confirmed!!)
                    Text("已确认选择，还没有记为吃过。用餐后再记入小本本。",fontSize=14.sp,color=AppColors.SubText)
                }
                result!=null -> {
                    MealDecisionTicket(result!!)
                    TextButton(onClick={
                        val d=result?.dish ?: return@TextButton
                        if(vm.skipDish(d.id)){result=null;frozen=null;notice="今天先跳过这一样，明天会恢复"} else reportFailure()
                    }){Text("今天不想吃")}
                }
                vm.shops.none { it.dishes.isNotEmpty() } -> {
                    Text("先放进几样爱吃的",fontSize=25.sp,color=AppColors.Text)
                    Text("从店铺添加菜单，或导入截图。识别后可以修改，确认后才保存。",color=AppColors.SubText)
                }
                meals.isEmpty() -> {
                    Text("这次没有符合条件的餐食",fontSize=25.sp,color=AppColors.Text)
                    Text("条件不会自动放宽。你可以调整其中一项。",color=AppColors.SubText)
                    vm.shops.flatMap { s -> s.dishes.mapNotNull { vm.blockReason(s,it) } }.groupingBy { it }.eachCount().forEach { (reason,count) ->
                        Text("$reason · $count 样",color=AppColors.Text,modifier=Modifier.padding(vertical=4.dp))
                    }
                }
                else -> {
                    MealSlotMachine(frozen?.takeIf { spinning } ?: items,{position.value},spinning,::spin)
                    TextButton(enabled=!spinning,onClick={candidatesOpen=true}){Text("${meals.size} 餐符合条件 · 查看")}
                }
            }
            if(!spinning && result==null && confirmed==null && saved==null && (vm.skip.dishIds.isNotEmpty() || vm.skip.shopIds.isNotEmpty())) {
                TextButton(onClick={if(vm.clearSkip()){notice="今天跳过的已恢复"}else reportFailure()}){Text("恢复今天跳过的选择")}
            }
        }
        HorizontalDivider(color=AppColors.Line)
        Column(Modifier.fillMaxWidth().background(AppColors.Surface).padding(horizontal=20.dp,vertical=10.dp)) {
            when {
                result!=null -> {
                    MealPrimaryAction("就吃这个",{confirmed=result;result=null},Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        TextButton(onClick={spin()}){Text("换一个")}
                        TextButton(onClick={editing=true}){Text("调整条件")}
                    }
                }
                confirmed!=null -> {
                    MealPrimaryAction("吃过了，记一餐",::confirm,Modifier.fillMaxWidth())
                    TextButton(onClick={result=confirmed;confirmed=null}){Text("重新考虑")}
                }
                saved!=null -> MealPrimaryAction("返回挑选",{saved=null},Modifier.fillMaxWidth())
                vm.shops.none{it.dishes.isNotEmpty()} -> MealPrimaryAction("添加菜单 / 导入截图",onGoShops,Modifier.fillMaxWidth())
                meals.isEmpty() -> MealPrimaryAction("调整条件",{editing=true},Modifier.fillMaxWidth())
                else -> Row(verticalAlignment=Alignment.CenterVertically) {
                    MealPrimaryAction(if(spinning)"正在挑选…" else if(meals.size==1)"看看这一餐" else "拉一下",::spin,Modifier.weight(1f),enabled=!spinning)
                    TextButton(enabled=!spinning,onClick={editing=true}){Text("条件")}
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealDecisionTicket(meal: DecisionResult.Meal) {
    val context=LocalContext.current
    val s=meal.shop; val d=meal.dish
    Text("这次的选择",fontSize=13.sp,color=AppColors.SubText,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
    Text("${s.emoji} ${d.name}",fontSize=27.sp,fontWeight=FontWeight.SemiBold,color=AppColors.Text)
    Text(s.name,fontSize=17.sp,color=AppColors.SubText)
    FlowRow(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(d.paid?.yuan() ?: "价格待确认",fontSize=26.sp,fontWeight=FontWeight.Bold,color=AppColors.Deep)
        Text(s.walkMinutes?.let{"步行约 $it 分钟"} ?: "路程待确认",fontSize=15.sp,color=AppColors.SubText)
    }
    Text(if(d.groupPrice!=null)"已录入团购价，付款前核对平台条件" else "已录入菜单价，实际价格以店铺为准",fontSize=13.sp,color=AppColors.SubText)
    if(d.priceConditions.isNotBlank()) Text(d.priceConditions,color=AppColors.Text,fontSize=14.sp)
    if(d.paid==null)Text("未知价格不代表免费或预算内",fontSize=14.sp,color=AppColors.Deep)
    HorizontalDivider(color=AppColors.Line)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        TextButton(onClick={openMap(context,s)}){Text("核对路线 ↗")}
        if(s.link.isNotBlank())TextButton(onClick={openLink(context,s.link)}){Text("店铺优惠 ↗")}
    }
    if(s.address.isNotBlank())Text(s.address,fontSize=13.sp,color=AppColors.SubText)
}
