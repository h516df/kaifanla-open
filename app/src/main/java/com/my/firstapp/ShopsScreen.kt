package com.my.firstapp

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ShopsScreen(vm: AppViewModel) {
    var openId by remember { mutableStateOf<Long?>(null) }
    val open = openId?.let { id -> vm.shops.find { it.id == id } }
    if (open != null) {
        BackHandler { openId = null }
        ShopDetail(vm, open, onBack = { openId = null })
    } else {
        ShopList(vm, onOpen = { openId = it })
    }
}

/** 相册多选：一次可以挑好几张截图（店铺页 + 团购页），会合并成一次导入。 */
@Composable
private fun rememberScreenshotPicker(onPicked: (List<android.net.Uri>) -> Unit) =
    rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }

@Composable
private fun ShopList(vm: AppViewModel, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    var showDssGuide by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var addingShop by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }

    val pickShots = rememberScreenshotPicker { uris -> vm.startPhotoImport(uris) }

    ScreenColumn {
        PageTitle("我的店铺 🏪", "${vm.shops.size} 家常去的小店")
        if (vm.shops.isEmpty()) {
            EmptyCard("🏪", "还没有店铺哦", "分享或选择店铺、菜单截图，核对之后再保存。")
        }
        vm.shops.forEach { shop ->
            CuteCard(onClick = { onOpen(shop.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShopArtwork(shop.emoji, shop.photo, Modifier.size(52.dp), fallbackSize = 52.dp)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            shop.name,
                            fontSize = 20.sp,
                            fontFamily = CuteFont,
                            color = AppColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(shopSummary(shop), fontSize = 13.sp, color = AppColors.SubText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (shop.address.isNotBlank()) {
                            Text(
                                "📍 ${shop.address}",
                                fontSize = 13.sp,
                                color = AppColors.SubText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text("›", fontSize = 26.sp, color = AppColors.SubText)
                }
            }
        }
        // 一级入口只有两个：导入是主路径，手动是兜底。
        GradientButton("📥 从截图导入", onClick = { showDssGuide = true }, modifier = Modifier.fillMaxWidth())
        SoftButton("＋ 手动添加", onClick = { showManual = true }, modifier = Modifier.fillMaxWidth())
    }

    if (showDssGuide) {
        DoushengshengGuideDialog(
            onOpenApp = { name -> openDoushengsheng(context, name) },
            onPickPhotos = { pickShots.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onDismiss = { showDssGuide = false },
        )
    }
    if (showManual) {
        ManualAddDialog(
            onManualShop = { showManual = false; addingShop = true },
            onPaste = { showManual = false; pasting = true },
            onPickPhotos = {
                showManual = false
                pickShots.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onDismiss = { showManual = false },
        )
    }
    if (addingShop) {
        ShopDialog(
            title = "添加店铺",
            initial = ShopInfo("", "🍽️"),
            onDismiss = { addingShop = false },
            onSave = { info ->
                val id = vm.addShop(info)
                if (id != null) {
                    addingShop = false
                    onOpen(id)
                } else Toast.makeText(context, "没保存成功，请再试一次", Toast.LENGTH_SHORT).show()
            },
        )
    }
    if (pasting) {
        PasteDialog(
            onDismiss = { pasting = false },
            onConfirm = { text -> pasting = false; vm.startPasteImport(text) },
        )
    }
}

private fun shopSummary(shop: Shop): String {
    val parts = mutableListOf<String>()
    if (shop.dishes.isNotEmpty()) {
        val prices = shop.dishes.mapNotNull { it.paid }
        val range = when {
            prices.isEmpty() -> ""
            prices.min() == prices.max() -> " · ${prices.min().yuan()}"
            else -> " · ${prices.min().yuan()} ~ ${prices.max().yuan()}"
        }
        parts += "${shop.dishes.size} 样吃的$range"
    }
    if (shop.deals.isNotEmpty()) parts += "${shop.deals.size} 个团购"
    return if (parts.isEmpty()) "还没有录入" else parts.joinToString(" · ")
}

/** 「从抖省省导入」的引导：讲清最短路径，并且能直接把抖省省拉起来。 */
@Composable
private fun DoushengshengGuideDialog(
    onOpenApp: (String) -> Unit,
    onPickPhotos: () -> Unit,
    onDismiss: () -> Unit,
) {
    var shopName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从截图导入 📥", fontFamily = CuteFont, fontSize = 24.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "支持堂食菜单、抖省省、美团、饿了么等截图。分享给「开饭啦」或选择图片，识别后先核对平台、金额和条件。请只截商家和报价区域，不含订单号、电话或核销码。",
                    fontSize = 14.sp,
                    color = AppColors.Text,
                )
                Text("仅已有抖省省入口支持辅助打开；其他平台请自行打开后截图，不能保证识别所有版式。", fontSize = 13.sp, color = AppColors.SubText)
                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it.replace("\n", "") },
                    label = { Text("店名（可不填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onOpenApp(shopName.trim()); onDismiss() }) { Text("打开抖省省") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPickPhotos(); onDismiss() }) { Text("选截图") }
                TextButton(onClick = onDismiss) { Text("好") }
            }
        },
        containerColor = AppColors.Surface,
    )
}

/** 「手动添加」把旧能力收进来，一级界面不再铺 OCR / 批量粘贴这些词。 */
@Composable
private fun ManualAddDialog(
    onManualShop: () -> Unit,
    onPaste: () -> Unit,
    onPickPhotos: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动添加 ✍️", fontFamily = CuteFont, fontSize = 24.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("🏪 手动填店铺信息", onClick = onManualShop, modifier = Modifier.fillMaxWidth())
                SoftButton("📋 批量粘贴菜单", onClick = onPaste, modifier = Modifier.fillMaxWidth())
                SoftButton("📷 从相册选择截图", onClick = onPickPhotos, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = AppColors.Surface,
    )
}

/** 批量粘贴：只负责收集文字，整理和确认都交给统一导入预览。 */
@Composable
private fun PasteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量粘贴菜单 📋", fontFamily = CuteFont, fontSize = 24.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "一行一样，名字后面写价格。写两个价格时，大的算原价、小的算团购价。",
                    fontSize = 13.sp,
                    color = AppColors.SubText,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("招牌麻辣烫 25 19.9\n番茄麻辣烫 25\n酸梅汤 6") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) { Text("下一步") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = AppColors.Surface,
    )
}

@Composable
private fun ShopDetail(vm: AppViewModel, shop: Shop, onBack: () -> Unit) {
    val context = LocalContext.current
    var moreOpen by remember { mutableStateOf(false) }
    var editingShop by remember { mutableStateOf(false) }
    var deletingShop by remember { mutableStateOf(false) }
    var updatingDeals by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    var editingDish by remember { mutableStateOf<Dish?>(null) }
    var editingDeal by remember { mutableStateOf<Deal?>(null) }

    val pickShots = rememberScreenshotPicker { uris -> vm.startPhotoImport(uris, shop.id) }

    ScreenColumn {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("‹ 我的店铺", fontSize = 16.sp) }
        }
        CuteCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (shop.photo.isBlank()) EmojiBadge(shop.emoji, size = 80.dp)
                else ShopArtwork(shop.emoji, shop.photo, Modifier.fillMaxWidth().height(150.dp), fallbackSize = 80.dp)
                Text(
                    shop.name,
                    fontSize = 26.sp,
                    fontFamily = CuteFont,
                    color = AppColors.Text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (shop.address.isNotBlank()) {
                    Text("📍 ${shop.address}", fontSize = 14.sp, color = AppColors.SubText, textAlign = TextAlign.Center)
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(
                        if (shop.address.isNotBlank()) "🗺️ 导航过去" else "🗺️ 地图搜店名",
                        onClick = { openMap(context, shop) },
                    )
                    SoftButton("🎫 去抖省省", onClick = { openDoushengsheng(context, shop.name, shop.link) })
                }
            }
        }

        // 一级操作只有两个：更新团购 + 更多。旧的那一排按钮收进「更多」。
        GradientButton("📥 更新团购", onClick = { updatingDeals = true }, modifier = Modifier.fillMaxWidth())
        SoftButton("⋯ 更多", onClick = { moreOpen = true }, modifier = Modifier.fillMaxWidth())

        SectionLabel("🍽️ 菜品（${shop.dishes.size}）· 参与转盘")
        if (shop.dishes.isEmpty()) {
            EmptyCard("🍽️", "还没有菜品", "更新团购时会一起把推荐菜认进来～")
        }
        shop.dishes.forEach { dish ->
            CuteCard(onClick = { editingDish = dish }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        dish.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    if(dish.priceConfirmed) PriceTag(dish.price, dish.groupPrice) else Text("价格待确认",fontSize=14.sp,color=AppColors.SubText)
                }
            }
        }

        SectionLabel("🎫 平台报价 / 套餐（${shop.deals.size}）")
        if (shop.deals.isEmpty()) {
            Text("还没有报价，可以从截图导入并核对，也可以手动记录。", fontSize = 13.sp, color = AppColors.SubText)
        }
        shop.deals.forEach { deal ->
            CuteCard(onClick = { editingDeal = deal }) {
                Column {
                    Text(
                        deal.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${importPlatformLabel(deal.platform)} · 观察时间${if(deal.observedAt==null)"未知" else java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.CHINA).format(java.util.Date(deal.observedAt))}",fontSize=14.sp,color=AppColors.SubText)
                    if(deal.importedAt>0)Text("导入于 "+java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.CHINA).format(java.util.Date(deal.importedAt)),fontSize=14.sp,color=AppColors.SubText)
                    if(deal.conditionsText.isNotBlank())Text(deal.conditionsText,fontSize=14.sp,color=AppColors.SubText)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                        PriceTag(deal.originalPrice, deal.currentPrice)
                        Text(if(deal.priceConfirmed)"已核对条件" else "条件待核对",fontSize=14.sp,color=AppColors.SubText)
                        val extra = listOf(deal.soldText, deal.tag).filter { it.isNotBlank() }.joinToString(" · ")
                        if (extra.isNotBlank()) {
                            Text(
                                extra,
                                fontSize = 12.sp,
                                color = AppColors.SubText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (updatingDeals) {
        AlertDialog(
            onDismissRequest = { updatingDeals = false },
            title = { Text("更新团购 📥", fontFamily = CuteFont, fontSize = 24.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "选择同一分店的店铺、报价和条件截图。不同平台报价并列保留，是否替换须在预览里确认。",
                        fontSize = 13.sp,
                        color = AppColors.SubText,
                    )
                    SoftButton("📷 选截图更新", onClick = {
                        updatingDeals = false
                        pickShots.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, modifier = Modifier.fillMaxWidth())
                    SoftButton("🎫 去抖省省截图", onClick = {
                        updatingDeals = false
                        openDoushengsheng(context, shop.name, shop.link)
                    }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { updatingDeals = false }) { Text("取消") } },
            containerColor = AppColors.Surface,
        )
    }

    if (moreOpen) {
        AlertDialog(
            onDismissRequest = { moreOpen = false },
            title = { Text("更多 ⋯", fontFamily = CuteFont, fontSize = 24.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("✍️ 编辑店铺信息", onClick = { moreOpen = false; editingShop = true }, modifier = Modifier.fillMaxWidth())
                    SoftButton("＋ 手动加一样菜", onClick = { moreOpen = false; editingDish = Dish(-1, "", null, null) }, modifier = Modifier.fillMaxWidth())
                    SoftButton("📋 批量粘贴菜单", onClick = { moreOpen = false; pasting = true }, modifier = Modifier.fillMaxWidth())
                    SoftButton("📷 从相册选择截图", onClick = {
                        moreOpen = false
                        pickShots.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { moreOpen = false; deletingShop = true }) {
                        Text("删除这家店", color = AppColors.SubText)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moreOpen = false }) { Text("好") } },
            containerColor = AppColors.Surface,
        )
    }

    if (pasting) {
        PasteDialog(
            onDismiss = { pasting = false },
            onConfirm = { text -> pasting = false; vm.startPasteImport(text, shop.id) },
        )
    }
    if (editingShop) {
        ShopDialog(
            title = "编辑店铺",
            initial = shop.info(),
            onDismiss = { editingShop = false },
            onSave = { info ->
                if (vm.updateShop(shop.id, info)) editingShop = false
                else Toast.makeText(context, "没保存成功，请再试一次", Toast.LENGTH_SHORT).show()
            },
        )
    }
    if (deletingShop) {
        AlertDialog(
            onDismissRequest = { deletingShop = false },
            title = { Text("删除「${shop.name}」？") },
            text = { Text("店里的 ${shop.dishes.size} 样菜品、${shop.deals.size} 个团购和封面照片会一起删掉，小本本里的记录会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    if (vm.deleteShop(shop.id)) {
                        deletingShop = false
                        onBack()
                    } else Toast.makeText(context, "没保存成功，请再试一次", Toast.LENGTH_SHORT).show()
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deletingShop = false }) { Text("算了") } },
            containerColor = AppColors.Surface,
        )
    }
    editingDish?.let { dish ->
        val isNew = dish.id == -1L
        DishDialog(
            dish = dish,
            isNew = isNew,
            onDismiss = { editingDish = null },
            onSave = { name, price, groupPrice, conditions, confirmed ->
                if (vm.saveDish(shop.id, if (isNew) null else dish.id, name, price, groupPrice, conditions, confirmed)) editingDish = null
                else Toast.makeText(context, "没保存成功，请再试一次", Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                if (vm.deleteDish(shop.id, dish.id)) editingDish = null
                else Toast.makeText(context, "没保存成功，请再试一次", Toast.LENGTH_SHORT).show()
            },
        )
    }
    editingDeal?.let { deal ->
        val isNew = deal.id == -1L
        DealDialog(
            deal = deal,
            isNew = isNew,
            onDismiss = { editingDeal = null },
            onSave = { title, current, original, conditions, confirmed ->
                if (vm.saveDeal(shop.id, if (isNew) null else deal.id, title, current, original, conditions, confirmed)) editingDeal = null
                else Toast.makeText(context, "没保存成功，请再试一次", Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                if (vm.deleteDeal(shop.id, deal.id)) editingDeal = null
                else Toast.makeText(context, "没保存成功，请再试一次", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun DishDialog(
    dish: Dish,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Double?, Double?, String, Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(dish.name) }
    var price by remember { mutableStateOf(dish.price?.yuan()?.removePrefix("¥") ?: "") }
    var group by remember { mutableStateOf(dish.groupPrice?.yuan()?.removePrefix("¥") ?: "") }
    var conditions by remember { mutableStateOf(dish.priceConditions) }
    var confirmed by remember { mutableStateOf(dish.priceConfirmed) }
    val priceOk = price.isBlank() || price.toDoubleOrNull()?.let{validImportMoney(it)} == true
    val groupOk = group.isBlank() || group.toDoubleOrNull()?.let{validImportMoney(it)} == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "添加菜品" else "修改菜品") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.replace("\n", "") },
                    label = { Text("名字，比如：招牌麻辣烫") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.trim(); confirmed=false },
                    label = { Text("原价（元，可不填）") },
                    singleLine = true,
                    isError = !priceOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it.trim(); confirmed=false },
                    label = { Text("团购价（元，可不填）") },
                    singleLine = true,
                    isError = !groupOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value=conditions,onValueChange={conditions=it;confirmed=false},label={Text("价格适用条件")},modifier=Modifier.fillMaxWidth())
                Row(verticalAlignment=Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked=confirmed,onCheckedChange={confirmed=it})
                    Text("金额和适用条件已核对",fontSize=14.sp,color=AppColors.Text)
                }
                if (!isNew) {
                    TextButton(onClick = onDelete) { Text("删除这样吃的", color = AppColors.SubText) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && priceOk && groupOk,
                onClick = { onSave(name.trim(), price.toDoubleOrNull(), group.toDoubleOrNull(), conditions, confirmed) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = AppColors.Surface,
    )
}

@Composable
private fun DealDialog(
    deal: Deal,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Double?, Double?, String, Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf(deal.title) }
    var current by remember { mutableStateOf(deal.currentPrice?.yuan()?.removePrefix("¥") ?: "") }
    var original by remember { mutableStateOf(deal.originalPrice?.yuan()?.removePrefix("¥") ?: "") }
    var conditions by remember { mutableStateOf(deal.conditionsText) }
    var confirmed by remember { mutableStateOf(deal.priceConfirmed) }
    val currentOk = current.isBlank() || current.toDoubleOrNull()?.let{validImportMoney(it)} == true
    val originalOk = original.isBlank() || original.toDoubleOrNull()?.let{validImportMoney(it)} == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "添加团购" else "修改团购") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("报价不会直接参与抽餐，请回对应平台核对适用条件及有效期。", fontSize = 12.sp, color = AppColors.SubText)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.replace("\n", "") },
                    label = { Text("团购标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it.trim(); confirmed=false },
                    label = { Text("当前价（元）") },
                    singleLine = true,
                    isError = !currentOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = original,
                    onValueChange = { original = it.trim(); confirmed=false },
                    label = { Text("划线原价（元，可不填）") },
                    singleLine = true,
                    isError = !originalOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value=conditions,onValueChange={conditions=it;confirmed=false},label={Text("价格适用条件")},modifier=Modifier.fillMaxWidth())
                Row(verticalAlignment=Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked=confirmed,onCheckedChange={confirmed=it})
                    Text("金额和适用条件已核对",fontSize=14.sp,color=AppColors.Text)
                }
                if (!isNew) {
                    TextButton(onClick = onDelete) { Text("删除这个团购", color = AppColors.SubText) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && currentOk && originalOk,
                onClick = { onSave(title.trim(), current.toDoubleOrNull(), original.toDoubleOrNull(), conditions, confirmed) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = AppColors.Surface,
    )
}

