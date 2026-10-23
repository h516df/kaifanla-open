package com.my.firstapp

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val updateSession = lazy { dev.h516.update.Updater(app.applicationContext) }
    val updater get() = updateSession.value
    override fun onCleared() {
        if (updateSession.isInitialized()) updateSession.value.close()
        super.onCleared()
    }
    private val store = AppDataStore(File(app.filesDir, "data.json"))
    private val photos = ShopPhotos(app)
    private val staging = ShareStaging(app)
    private var lastId = System.currentTimeMillis()
    private var loadFailed = false
    private var lastSaved = AppData(emptyList(), emptyList(), Settings(), SkipToday())

    var shops by mutableStateOf<List<Shop>>(emptyList())
        private set
    var history by mutableStateOf<List<Record>>(emptyList())
        private set
    var settings by mutableStateOf(Settings())
        private set
    private var skipState by mutableStateOf(SkipToday())
    var storageError by mutableStateOf<String?>(null)
        private set
    var autoBackupWarning by mutableStateOf<String?>(null)
        private set

    /** 待确认的导入草稿。分享进来、相册选图、批量粘贴最后都落到这里，预览页只出现一次。 */
    var importDraft by mutableStateOf<ImportDraft?>(null)
        private set
    /** 区分先后两次预览，避免新分享沿用上一份输入框状态。 */
    private var importRequestGeneration = 0L
    var importSessionId by mutableStateOf(0L)
        private set
    /** 正在 OCR 时的提示文案；null 表示没在忙。 */
    var importBusy by mutableStateOf<String?>(null)
        private set
    var importError by mutableStateOf<String?>(null)
        private set

    /** 今天的「不想吃」名单，过了零点自动清空。 */
    val skip: SkipToday
        get() = skipState.takeIf { it.day == today() } ?: SkipToday(today())

    init {
        when (val loaded = store.load()) {
            DataLoad.Missing -> shops = sampleShops()
            is DataLoad.Loaded -> applyData(loaded.data)
            is DataLoad.Failed -> {
                loadFailed = true
                storageError = "存档读取失败，原文件已保留。编辑不会保存，请先联系维护者处理。"
            }
        }
        lastSaved = snapshot()
        applyTheme()
        runDailyAutoBackup(app)
    }

    private fun runDailyAutoBackup(app: Application) {
        if (loadFailed) return
        val day = today()
        if (settings.lastAutoBackupDay == day) return
        val updatedSettings = settings.copy(lastAutoBackupDay = day)
        val backupJson = DataJson.encode(snapshot().copy(settings = updatedSettings))
        try {
            if (!AutoBackupStore(app.contentResolver).backup(day, backupJson)) return
            settings = updatedSettings
            autoBackupWarning = if (save()) null else "自动备份已经写入下载文件夹，但本地状态没保存成功。"
        } catch (_: Exception) {
            autoBackupWarning = "今天自动备份没成功，可以在「我的」里手动备份。"
        }
    }

    private fun newId() = ++lastId

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())

    private fun snapshot() = AppData(shops, history, settings, skipState)

    private fun applyData(data: AppData) {
        shops = data.shops
        history = data.history
        settings = data.settings
        skipState = data.skip
    }

    private fun save(): Boolean {
        if (loadFailed) {
            applyData(lastSaved)
            applyTheme()
            return false
        }
        val data = snapshot()
        return try {
            store.save(data)
            lastSaved = data
            storageError = null
            true
        } catch (_: Exception) {
            applyData(lastSaved)
            applyTheme()
            storageError = "存档保存失败，修改已撤回，原文件仍在。请稍后重试。"
            false
        }
    }

    private fun applyTheme() {
        AppColors.palette = Palettes.find { it.id == settings.theme } ?: Palettes.first()
    }

    // ---------- 设置 ----------

    fun updateSettings(change: (Settings) -> Settings): Boolean {
        settings = change(settings)
        applyTheme()
        return save()
    }

    // ---------- 转盘规则 ----------

    private fun recentCutoff(): Long? {
        if (settings.avoidDays <= 0) return null
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(settings.avoidDays - 1))
        }.timeInMillis
    }

    /** 这样吃的现在为什么不能上转盘；能上返回 null。 */
    fun blockReason(shop: Shop, dish: Dish): String? =
        mealBlockReason(shop, dish, skip, settings.budget, history, recentCutoff(),
            settings.maxWalkMinutes, settings.allowUnknownPrice, settings.allowUnknownWalk)

    fun eligibleDishes(shop: Shop): List<Dish> = shop.dishes.filter { blockReason(shop, it) == null }

    fun eligibleShops(): List<Shop> = shops.filter { eligibleDishes(it).isNotEmpty() }

    /**
     * 「直接抽吃的」用：从所有能抽的吃的里随机挑 count 样。
     * 轮流从不同的店里拿，尽量让转盘上的店不重样。
     */
    fun sampleDishes(count: Int, random: Random): List<Pair<Shop, Dish>> =
        pickVariety(eligibleShops().map { it to eligibleDishes(it) }, count, random)

    fun skipShop(id: Long) = setSkip(skip.copy(shopIds = skip.shopIds + id))
    fun skipDish(id: Long) = setSkip(skip.copy(dishIds = skip.dishIds + id))
    fun unskipShop(id: Long) = setSkip(skip.copy(shopIds = skip.shopIds - id))
    fun unskipDish(id: Long) = setSkip(skip.copy(dishIds = skip.dishIds - id))
    fun clearSkip() = setSkip(SkipToday(today()))

    private fun setSkip(value: SkipToday): Boolean {
        skipState = value
        return save()
    }

    // ---------- 店铺和吃的 ----------

    fun addShop(info: ShopInfo): Long? {
        val id = newId()
        val photo = try {
            info.newPhoto?.let(photos::import) ?: ""
        } catch (_: Exception) {
            storageError = "照片没保存好，请换一张再试。"
            return null
        }
        shops = shops + Shop(id, info.name, info.emoji, emptyList(), info.address, info.link, photo, walkMinutes = info.walkMinutes)
        return if (save()) id else {
            photos.delete(photo)
            null
        }
    }

    fun updateShop(id: Long, info: ShopInfo): Boolean {
        val old = shops.find { it.id == id } ?: return false
        val photo = try {
            info.newPhoto?.let(photos::import) ?: info.photo
        } catch (_: Exception) {
            storageError = "照片没保存好，请换一张再试。"
            return false
        }
        shops = shops.map {
            if (it.id == id) it.copy(name = info.name, emoji = info.emoji, address = info.address, link = info.link, photo = photo, walkMinutes = info.walkMinutes) else it
        }
        return if (save()) {
            if (old.photo != photo) photos.delete(old.photo)
            true
        } else {
            if (info.newPhoto != null) photos.delete(photo)
            false
        }
    }

    fun deleteShop(id: Long): Boolean {
        val old = shops.find { it.id == id } ?: return false
        shops = shops.filterNot { it.id == id }
        return save().also { if (it) photos.delete(old.photo) }
    }

    /** dishId 为空表示新加一样吃的，否则是修改。 */
    fun saveDish(shopId: Long, dishId: Long?, name: String, price: Double?, groupPrice: Double?,
        conditions: String? = null, confirmed: Boolean? = null): Boolean {
        if(!validImportMoney(price) || !validImportMoney(groupPrice) || name.isBlank())return false
        return updateDishes(shopId) { dishes ->
            val old=dishes.find{it.id==dishId}
            val dish = (old ?: Dish(dishId ?: newId(),name,price,groupPrice)).copy(name=name,price=price,groupPrice=groupPrice,
                priceConditions=conditions ?: old?.priceConditions.orEmpty(),priceConfirmed=confirmed ?: old?.priceConfirmed ?: true)
            if (dishId == null) dishes + dish else dishes.map { if (it.id == dishId) dish else it }
        }
    }

    /** 批量添加：同名的更新价格，新的加到后面。 */
    fun addDishes(shopId: Long, parsed: List<ParsedDish>): Boolean =
        updateDishes(shopId) { dishes ->
            var result = dishes
            parsed.forEach { p ->
                result = if (result.any { it.name == p.name }) {
                    result.map { if (it.name == p.name) it.copy(price = p.price, groupPrice = p.groupPrice) else it }
                } else {
                    result + Dish(newId(), p.name, p.price, p.groupPrice)
                }
            }
            result
        }

    fun deleteDish(shopId: Long, dishId: Long): Boolean =
        updateDishes(shopId) { dishes -> dishes.filterNot { it.id == dishId } }

    private fun updateDishes(shopId: Long, change: (List<Dish>) -> List<Dish>): Boolean {
        shops = shops.map { if (it.id == shopId) it.copy(dishes = change(it.dishes)) else it }
        return save()
    }

    // ---------- 团购（不进转盘） ----------

    /** dealId 为空表示新增一条团购，否则是修改。 */
    fun saveDeal(shopId: Long, dealId: Long?, title: String, currentPrice: Double?, originalPrice: Double?,
        conditions: String? = null, confirmed: Boolean? = null): Boolean {
        if(title.isBlank() || !validImportMoney(currentPrice) || !validImportMoney(originalPrice))return false
        return updateDeals(shopId) { deals ->
            if(dealId==null) deals+Deal(newId(),title,currentPrice,originalPrice,source="manual",
                conditionsText=conditions.orEmpty(),priceConfirmed=confirmed ?: false)
            else deals.map { if(it.id==dealId)it.copy(title=title,currentPrice=currentPrice,originalPrice=originalPrice,
                conditionsText=conditions ?: it.conditionsText,priceConfirmed=confirmed ?: it.priceConfirmed,
                observedAt=if(currentPrice!=it.currentPrice || originalPrice!=it.originalPrice)null else it.observedAt) else it }
        }
    }

    fun deleteDeal(shopId: Long, dealId: Long): Boolean =
        updateDeals(shopId) { deals -> deals.filterNot { it.id == dealId } }

    private fun updateDeals(shopId: Long, change: (List<Deal>) -> List<Deal>): Boolean {
        shops = shops.map { if (it.id == shopId) it.copy(deals = change(it.deals)) else it }
        return save()
    }

    // ---------- 导入（分享 / 相册 / 粘贴 都走这里，最后只确认一次） ----------

    /** 把草稿接到现有店铺上再显示，预览页才知道哪些是「更新」、哪些菜已经有了。 */
    fun openImport(draft: ImportDraft) {
        importRequestGeneration++
        importBusy = null
        importError = null
        importSessionId++
        importDraft = attachToShops(shops, draft)
    }

    /** 编辑留在已有 ViewModel，旋转不丢草稿，也不重新按改过的店名匹配 ID。 */
    fun updateImportDraft(draft: ImportDraft, session: Long = importSessionId) {
        val previous = importDraft ?: return
        if (session != importSessionId) return
        importDraft = if(previous.platform != draft.platform) {
            attachToShops(shops, draft.copy(deals=draft.deals.map {
                it.copy(platform=draft.platform,priceConfirmed=false,resolution=DealResolution.ADD,existingId=null,
                    suspectedExistingId=null,expectedExisting=null)
            }))
        } else draft
    }

    fun cancelImport(session: Long = importSessionId) {
        if(session != importSessionId)return
        importRequestGeneration++
        importDraft = null
        importBusy = null
        staging.clear()
    }

    fun confirmImport(draft: ImportDraft, session: Long = importSessionId): Boolean {
        if(importDraft == null || session != importSessionId) return false
        try {
            shops = applyDraft(shops, draft, ::newId, System.currentTimeMillis())
        } catch (e: ImportValidationException) {
            importError = e.message ?: "请重新核对导入内容"
            return false
        }
        val ok = save()
        if (ok) {
            importDraft = null
            staging.clear()
        }
        return ok
    }

    fun dismissImportError() {
        importError = null
    }

    /** 系统分享进来的内容。图片先落 cache 再 OCR，避免分享 URI 权限失效。 */
    fun startShareImport(payload: SharePayload) {
        if (payload.isEmpty) return
        val request=++importRequestGeneration
        importDraft = null
        importSessionId++
        importError = null
        importBusy = if (payload.imageUris.size > 1) "正在认 ${payload.imageUris.size} 张截图…" else "正在认字…"
        viewModelScope.launch {
            runImport(
                uris = payload.imageUris,
                text = payload.text,
                source = if(payload.imageUris.isEmpty()) "text" else "photo",
                sourceUrl = parseSharedText(payload.text).url,
                knownShopId = null,
                requestId = request,
            )
        }
    }

    /** App 内「从相册选择截图」。和分享进来走同一套识别与预览。 */
    fun startPhotoImport(uris: List<Uri>, knownShopId: Long? = null) {
        if (uris.isEmpty()) return
        importDraft = null
        importSessionId++
        importError = null
        importBusy = if (uris.size > 1) "正在认 ${uris.size} 张截图…" else "正在认字…"
        val request=++importRequestGeneration
        viewModelScope.launch { runImport(uris = uris, text = "", source = "photo", sourceUrl = "", knownShopId = knownShopId, requestId=request) }
    }

    /** 「批量粘贴」：老能力保留，但同样汇入统一预览页。 */
    fun startPasteImport(text: String, knownShopId: Long? = null) {
        val dishes = ocrLinesToDraftDishes(text.lines().map(::OcrLine))
        if (dishes.isEmpty()) {
            importError = "这段文字里没认出菜名，一行一样、名字后面写价格试试～"
            return
        }
        openImport(
            ImportDraft(
                dishes = dishes,
                source = "paste",
                rawText = text,
                matchedShopId = knownShopId,
            ),
        )
    }

    private suspend fun runImport(
        uris: List<Uri>,
        text: String,
        source: String,
        sourceUrl: String,
        knownShopId: Long?,
        requestId: Long,
    ) {
        try {
            if(requestId!=importRequestGeneration)return
            val files = staging.stage(uris)
            val scans = mutableListOf<PageScan>()
            files.forEachIndexed { index, file ->
                if(requestId!=importRequestGeneration)return
                if (files.size > 1) importBusy = "正在认第 ${index + 1}/${files.size} 张…"
                val lines = runCatching { recognizeMenuLines(getApplication(), Uri.fromFile(file)) }
                    .onFailure { Log.w("Import", "分享图片识别失败：${it::class.java.simpleName}") }
                    .getOrDefault(emptyList())
                if (lines.isNotEmpty()) scans += scanForImport(lines)
            }
            if(requestId!=importRequestGeneration)return
            if (text.isNotBlank()) scans += scanSharedText(text)

            var draft = mergeScans(scans, source = source, sourceUrl = sourceUrl)
            if (knownShopId != null) draft = draft.copy(matchedShopId = knownShopId)

            importBusy = null
            if (draft.shopName.isBlank() && draft.deals.isEmpty() && draft.dishes.isEmpty()) {
                importError = "这几张图没认出店铺或团购，换清楚点的截图再试试～"
                staging.clear()
            } else {
                openImport(draft)
            }
        } catch (e: Exception) {
            if(requestId!=importRequestGeneration)return
            Log.w("Import", "导入失败：${e::class.java.simpleName}")
            importBusy = null
            importError = "导入没成功，请再试一次"
            staging.clear()
        }
    }

    // ---------- 小本本 ----------

    fun addRecord(shop: Shop, dish: Dish): Boolean {
        val record = Record(System.currentTimeMillis(), shop.emoji, shop.name, dish.name, dish.price.takeIf{dish.priceConfirmed}, dish.groupPrice.takeIf{dish.priceConfirmed})
        history = listOf(record) + history
        return save()
    }

    fun deleteRecord(record: Record): Boolean {
        history = history - record
        return save()
    }

    fun clearHistory(): Boolean {
        history = emptyList()
        return save()
    }

    // ---------- 备份和分享 ----------

    private fun shopsWithoutPhotos() = shops.map { it.copy(photo = "") }

    fun exportJson(): String {
        check(!loadFailed) { "Cannot export unreadable data" }
        return DataJson.encode(snapshot().copy(shops = shopsWithoutPhotos()))
    }

    /** 用备份文件覆盖现在的店铺和记录，设置保留。 */
    fun restore(text: String): Boolean = runCatching {
        val data = DataJson.decode(DataJson.extract(text))
        val oldPhotos = shops.map { it.photo }
        // JSON 备份只包含店铺资料，没有照片文件。
        shops = data.shops.map { it.copy(photo = "") }
        history = data.history
        save().also { saved -> if (saved) oldPhotos.forEach(photos::delete) }
    }.getOrDefault(false)

    /** 前面是给人看的菜单，后面是给 App 导入用的数据。 */
    fun shareMenuText(): String = buildString {
        appendLine("【开饭啦 · 我的店铺菜单】")
        shops.forEach { shop ->
            appendLine()
            appendLine("${shop.emoji} ${shop.name}")
            shop.dishes.forEach { d ->
                val price = when {
                    d.groupPrice != null && d.price != null -> "团购${d.groupPrice.yuan()}（原价${d.price.yuan()}）"
                    d.paid != null -> d.paid!!.yuan()
                    else -> ""
                }
                appendLine("  · ${d.name} $price".trimEnd())
            }
        }
        appendLine()
        appendLine("有「开饭啦」App 的话：长按复制这一整段，在「我的 → 粘贴导入菜单」里粘贴就能用～")
        append(DataJson.encode(AppData(shopsWithoutPhotos(), emptyList(), Settings(), SkipToday())))
    }

    /** 合并别人分享的菜单：新店直接加，已有的店只补上没有的菜。返回有变化的店数，失败返回 -1。 */
    fun mergeMenu(text: String): Int {
        val incoming = runCatching { DataJson.decode(DataJson.extract(text)).shops }.getOrNull() ?: return -1
        var result = shops
        var changed = 0
        incoming.forEach { inc ->
            val existing = result.find { it.name == inc.name }
            if (existing == null) {
                result = result + inc.copy(id = newId(), photo = "", dishes = inc.dishes.map { it.copy(id = newId()) })
                changed++
            } else {
                val extra = inc.dishes.filter { d -> existing.dishes.none { it.name == d.name } }.map { it.copy(id = newId()) }
                if (extra.isNotEmpty()) {
                    result = result.map { if (it.id == existing.id) it.copy(dishes = it.dishes + extra) else it }
                    changed++
                }
            }
        }
        shops = result
        return if (save()) changed else -1
    }

    /** 第一次打开时放几家示例店，方便看效果，可以随便改删。 */
    private fun sampleShops(): List<Shop> {
        fun dish(name: String, price: Double?, group: Double? = null) = Dish(newId(), name, price, group)
        return listOf(
            Shop(newId(), "蜜雪冰城", "🧋", listOf(
                dish("冰鲜柠檬水", 4.0),
                dish("珍珠奶茶", 7.0, 6.0),
                dish("草莓摇摇奶昔", 8.0, 6.9),
            )),
            Shop(newId(), "麦当劳", "🍔", listOf(
                dish("巨无霸套餐", 39.0, 29.9),
                dish("麦辣鸡腿堡套餐", 37.0, 26.9),
                dish("板烧鸡腿堡", 23.0),
            )),
            Shop(newId(), "杨国福麻辣烫", "🍲", listOf(
                dish("招牌麻辣烫", 25.0, 19.9),
                dish("番茄麻辣烫", 25.0, 19.9),
            )),
            Shop(newId(), "沙县小吃", "🥟", listOf(
                dish("拌面加扁肉", 12.0),
                dish("蒸饺", 8.0),
                dish("鸡腿饭", 16.0),
            )),
        )
    }
}

