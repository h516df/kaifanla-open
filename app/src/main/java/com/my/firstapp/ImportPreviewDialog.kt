package com.my.firstapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A review page, not an automatic price importer. Nothing is persisted until confirmation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportPreviewScreen(draft:ImportDraft,onDraftChanged:(ImportDraft)->Unit,onDismiss:()->Unit,onConfirm:(ImportDraft)->Unit) {
    BackHandler(onBack=onDismiss)
    val selectedDeals=draft.deals.filter{it.checked && it.resolution!=DealResolution.KEEP_EXISTING}
    val selectedDishes=draft.dishes.filter{it.checked}
    val valid=selectedDeals.all{it.title.isNotBlank() && validImportMoney(it.currentPrice) && validImportMoney(it.originalPrice) && (it.observedAt==null || it.observedAt>0)} &&
        selectedDishes.all{it.name.isNotBlank() && validImportMoney(it.price) && validImportMoney(it.groupPrice)}
    ScreenColumn {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("核对这次导入",fontFamily=CuteFont,fontSize=26.sp,color=AppColors.Text,modifier=Modifier.weight(1f))
            TextButton(onClick=onDismiss){Text("取消")}
        }
        Text("截图价格不是实时价格。先核对店铺、平台和适用条件，确认后才保存。",fontSize=14.sp,color=AppColors.SubText)
        CuteCard {
            OutlinedTextField(value=draft.shopName,onValueChange={onDraftChanged(draft.copy(shopName=it.replace("\n","")))},
                label={Text("店铺 / 分店名称")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=draft.address,onValueChange={onDraftChanged(draft.copy(address=it.replace("\n","")))},
                label={Text("地址，可留空")},modifier=Modifier.fillMaxWidth())
            Text(if(draft.isNewShop)"将新建这家店；分店请核对清楚。" else "将加入已选店铺；旧菜单和报价不会因截图缺席而删除。",fontSize=14.sp,color=AppColors.SubText)
            Text("价格来自哪里？",fontWeight=FontWeight.Bold,color=AppColors.Text)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("" to "待确认","dine_in" to "堂食","doushengsheng" to "抖省省","meituan" to "美团","eleme" to "饿了么","other" to "其他").forEach{(id,label)->
                    Pill(label,active=draft.platform==id){onDraftChanged(draft.copy(platform=id))}
                }
            }
            Text("导入时间：确认保存时记录。价格观察时间默认未知，不用导入时间冒充更新日期。",fontSize=14.sp,color=AppColors.SubText)
        }
        draft.notes.forEach{Text("请核对：$it",fontSize=14.sp,color=AppColors.Deep)}
        if(draft.deals.isNotEmpty()) {
            SectionLabel("套餐 / 优惠报价 · ${draft.deals.size} 项")
            Text("整份套餐、代金券和单菜分开保存，不拆算人均价，不直接参与抽餐。",fontSize=14.sp,color=AppColors.SubText)
            draft.deals.forEachIndexed{index,deal->key(deal.draftId){
                DealEditRow(deal,draft){updated->onDraftChanged(draft.copy(deals=draft.deals.toMutableList().also{it[index]=updated}))}
            }}
        }
        if(draft.dishes.isNotEmpty()) {
            SectionLabel("菜单里的菜品 · ${draft.dishes.size} 项")
            draft.dishes.forEachIndexed{index,dish->key(dish.draftId){
                DishEditRow(dish){updated->onDraftChanged(draft.copy(dishes=draft.dishes.toMutableList().also{it[index]=updated}))}
            }}
        }
        if(draft.missingDeals.isNotEmpty())Text("本次未出现的旧报价仍保留：${draft.missingDeals.joinToString("、")}",fontSize=14.sp,color=AppColors.SubText)
        EvidenceText(draft.rawText)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(checked=draft.retainRawText,onCheckedChange={onDraftChanged(draft.copy(retainRawText=it))})
            Text("在本机保留识别原文作证据（备份也会包含）",fontSize=14.sp,color=AppColors.Text)
        }
        Text("默认只保存核对后的内容和条件，不永久保存截图。若保留原文，请展开各项删除电话、订单号和核销码。",fontSize=14.sp,color=AppColors.SubText)
        if(!valid)Text("金额须为非负数字、日期须有效，名称不能留空。未知价格请留空，不填 0。",fontSize=14.sp,color=AppColors.Deep)
        val count=selectedDeals.size+selectedDishes.size
        GradientButton("确认导入 $count 项",{onConfirm(draft.copy(shopName=draft.shopName.trim(),address=draft.address.trim()))},
            Modifier.fillMaxWidth(),enabled=valid && count>0 && draft.shopName.isNotBlank())
    }
}

private fun parseImportMoney(value:String):Double?=if(value.isBlank())null else value.toDoubleOrNull()?:Double.NaN
internal fun importPlatformLabel(value:String):String=when(value){"dine_in"->"堂食";"doushengsheng"->"抖省省";"meituan"->"美团";"eleme"->"饿了么";"other"->"其他";else->"来源待确认"}
private fun observedLabel(time:Long?):String=time?.let{SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(Date(it))}?:"未知"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DealEditRow(deal:DraftDeal,draft:ImportDraft,onUpdate:(DraftDeal)->Unit) {
    var current by rememberSaveable(deal.draftId){mutableStateOf(deal.currentPrice?.takeIf{it.isFinite()}?.yuan()?.removePrefix("¥").orEmpty())}
    var original by rememberSaveable(deal.draftId){mutableStateOf(deal.originalPrice?.takeIf{it.isFinite()}?.yuan()?.removePrefix("¥").orEmpty())}
    var observed by rememberSaveable(deal.draftId){mutableStateOf(deal.observedAt?.let{SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(Date(it))}.orEmpty())}
    val platform=importPlatform(deal.platform.ifBlank{draft.platform},draft.source)
    val old=deal.expectedExisting
    val replaceAllowed=old!=null && replacementAllowed(old,deal,draft)
    CuteCard {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(checked=deal.checked,onCheckedChange={onUpdate(deal.copy(checked=it))})
            Text("${importPlatformLabel(platform)} · 报价",fontSize=14.sp,color=AppColors.SubText)
        }
        OutlinedTextField(value=deal.title,onValueChange={onUpdate(deal.copy(title=it.replace("\n","")))},label={Text("套餐 / 优惠名称")},modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value=current,onValueChange={current=it.trim();onUpdate(deal.copy(currentPrice=parseImportMoney(current),priceConfirmed=false))},label={Text("页面报价，元")},
                isError=!validImportMoney(deal.currentPrice),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.weight(1f))
            OutlinedTextField(value=original,onValueChange={original=it.trim();onUpdate(deal.copy(originalPrice=parseImportMoney(original),priceConfirmed=false))},label={Text("明确的原价")},
                isError=!validImportMoney(deal.originalPrice),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.weight(1f))
        }
        OutlinedTextField(value=deal.conditionsText,onValueChange={onUpdate(deal.copy(conditionsText=it,priceConfirmed=false))},label={Text("会员、券后、份量、有效期等原条件")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=observed,onValueChange={observed=it.trim();onUpdate(deal.copy(observedAt=parseObservedDate(observed)))},
            label={Text("确知的观察日期 YYYY-MM-DD，可留空")},isError=deal.observedAt!=null && deal.observedAt<=0,modifier=Modifier.fillMaxWidth())
        Text("未填日期表示未知。金额为人民币；份量或有效期没写清就按未知处理。",fontSize=14.sp,color=AppColors.SubText)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(checked=deal.priceConfirmed,onCheckedChange={onUpdate(deal.copy(priceConfirmed=it))})
            Text("我已核对金额和条件适用于这次用餐",fontSize=14.sp,color=AppColors.Text)
        }
        if(old!=null) {
            HorizontalDivider(color=AppColors.Line)
            Text(if(replaceAllowed)"发现同平台同商品的旧报价，请选择" else "发现相似名称，不会自动覆盖旧报价",fontWeight=FontWeight.Bold,fontSize=14.sp,color=AppColors.Text)
            Text("旧：${old.title} · ${old.currentPrice?.yuan()?:"价格未知"} · ${importPlatformLabel(old.platform)}",fontSize=14.sp,color=AppColors.SubText)
            if(old.conditionsText.isNotBlank())Text("旧条件：${old.conditionsText}",fontSize=14.sp,color=AppColors.SubText)
            Text("旧原价：${old.originalPrice?.yuan()?:"未知"} · 旧观察日期：${observedLabel(old.observedAt)}",fontSize=14.sp,color=AppColors.SubText)
            if(old.sourceUrl.isNotBlank())Text("旧来源链接：${old.sourceUrl}",fontSize=14.sp,color=AppColors.SubText)
            if(replaceAllowed)Text("替换会完整采用这次核对的报价；新报价留空的原价、日期、链接和条件也会清空旧值。",fontSize=14.sp,color=AppColors.Deep)
            Text("新：${deal.currentPrice?.takeIf{it.isFinite()}?.yuan()?:"价格未知"} · ${deal.conditionsText.ifBlank{"条件待核对"}}",fontSize=14.sp,color=AppColors.Text)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Pill("另存新报价",active=deal.resolution==DealResolution.ADD){onUpdate(deal.copy(resolution=DealResolution.ADD,existingId=null))}
                Pill("跳过这项",active=deal.resolution==DealResolution.KEEP_EXISTING){onUpdate(deal.copy(resolution=DealResolution.KEEP_EXISTING,existingId=null))}
                if(replaceAllowed)Pill("替换这条旧报价",active=deal.resolution==DealResolution.REPLACE){onUpdate(deal.copy(resolution=DealResolution.REPLACE,existingId=old.id))}
            }
        }
        EvidenceText(deal.rawText){onUpdate(deal.copy(rawText=it))}
    }
}

@Composable
private fun DishEditRow(dish:DraftDish,onUpdate:(DraftDish)->Unit) {
    var price by rememberSaveable(dish.draftId){mutableStateOf(dish.price?.takeIf{it.isFinite()}?.yuan()?.removePrefix("¥").orEmpty())}
    var groupPrice by rememberSaveable(dish.draftId){mutableStateOf(dish.groupPrice?.takeIf{it.isFinite()}?.yuan()?.removePrefix("¥").orEmpty())}
    CuteCard {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(checked=dish.checked,onCheckedChange={onUpdate(dish.copy(checked=it))})
            Text(if(dish.existing)"已有同名菜，保留原菜单" else "新菜品",fontSize=14.sp,color=AppColors.SubText)
        }
        OutlinedTextField(value=dish.name,onValueChange={onUpdate(dish.copy(name=it.replace("\n","")))},label={Text("菜名")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=price,onValueChange={price=it.trim();onUpdate(dish.copy(price=parseImportMoney(price),priceConfirmed=false))},label={Text("这道菜的已核对价格，元")},
            isError=!validImportMoney(dish.price),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=groupPrice,onValueChange={groupPrice=it.trim();onUpdate(dish.copy(groupPrice=parseImportMoney(groupPrice),priceConfirmed=false))},label={Text("明确适用的团购价，可清空")},
            isError=!validImportMoney(dish.groupPrice),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=dish.priceConditions,onValueChange={onUpdate(dish.copy(priceConditions=it,priceConfirmed=false))},label={Text("价格适用条件，未知可留空")},modifier=Modifier.fillMaxWidth())
        Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(checked=dish.priceConfirmed,onCheckedChange={onUpdate(dish.copy(priceConfirmed=it))})
            Text("金额和条件已核对；未确认时仅作价格待确认",fontSize=14.sp,color=AppColors.Text)
        }
        EvidenceText(dish.rawText){onUpdate(dish.copy(rawText=it))}
    }
}

@Composable
private fun EvidenceText(raw:String,onEdit:((String)->Unit)?=null) {
    if(raw.isBlank())return
    var expanded by rememberSaveable{mutableStateOf(false)}
    TextButton(onClick={expanded=!expanded}){Text(if(expanded)"收起识别原文" else "对照识别原文")}
    if(expanded) {
        if(onEdit==null)Text(raw,fontSize=14.sp,color=AppColors.SubText)
        else OutlinedTextField(value=raw,onValueChange=onEdit,label={Text("识别原文，可删除无关隐私内容")},maxLines=6,modifier=Modifier.fillMaxWidth())
    }
}
