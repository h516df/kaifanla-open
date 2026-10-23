package com.my.firstapp

import java.util.UUID

/** Evidence is retained independently from editable recognition suggestions. */
data class ImportDraft(
    val shopName: String = "",
    val address: String = "",
    val emoji: String = "🍽️",
    val source: String = "",
    val sourceUrl: String = "",
    val deals: List<DraftDeal> = emptyList(),
    val dishes: List<DraftDish> = emptyList(),
    val matchedShopId: Long? = null,
    val missingDeals: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val platform: String = "",
    val rawText: String = "",
    val retainRawText: Boolean = false,
) {
    val checkedDeals: List<DraftDeal> get() = deals.filter { it.checked }
    val checkedDishes: List<DraftDish> get() = dishes.filter { it.checked }
    val isNewShop: Boolean get() = matchedShopId == null
}

enum class DealResolution { ADD, KEEP_EXISTING, REPLACE }
class ImportValidationException(message: String) : IllegalArgumentException(message)

data class DraftDeal(
    val title: String,
    val currentPrice: Double? = null,
    val originalPrice: Double? = null,
    val soldText: String = "",
    val tag: String = "",
    val sourceUrl: String = "",
    val checked: Boolean = true,
    /** Set only when the user explicitly selects REPLACE after reviewing the old snapshot. */
    val existingId: Long? = null,
    val rawText: String = "",
    val conditionsText: String = "",
    val platform: String = "",
    val sourceItemId: String = "",
    val observedAt: Long? = null,
    val priceConfirmed: Boolean = false,
    val suspectedExistingId: Long? = null,
    val resolution: DealResolution = DealResolution.ADD,
    val expectedExisting: Deal? = null,
    val draftId: String = UUID.randomUUID().toString(),
) {
    val isUpdate: Boolean get() = resolution == DealResolution.REPLACE && existingId != null
}

data class DraftDish(
    val name: String,
    val price: Double? = null,
    val groupPrice: Double? = null,
    val checked: Boolean = true,
    val existing: Boolean = false,
    val rawText: String = "",
    val priceConditions: String = "",
    val priceConfirmed: Boolean = true,
    val draftId: String = UUID.randomUUID().toString(),
)

// Match exactly the name we will save; do not normalize existing user data.
private val DraftDish.savedName: String get() = name.trim()

internal fun normShopName(raw: String): String = raw.replace('\u3000', ' ')
    .replace(Regex("\\s+"), "").replace('（', '(').replace('）', ')').trim()
internal fun normDealTitle(raw: String): String = raw.replace('\u3000', ' ')
    .replace(Regex("\\s+"), "").trimEnd('…', '.', '。', '·').trim()

/** Old source values may name a platform. Photo/paste/text are NEVER platform identities. */
internal fun importPlatform(platform: String, source: String = ""): String =
    platform.trim().lowercase().ifBlank {
        source.takeIf { it in setOf("doushengsheng", "douyin", "meituan", "dianping", "eleme") }.orEmpty()
    }

/** Same-looking cards remain separate evidence; no last-image-wins price replacement. */
fun mergeScans(scans: List<PageScan>, source: String, sourceUrl: String = ""): ImportDraft {
    val platform = importPlatform("", source)
    val raw = scans.map { it.rawText }.filter { it.isNotBlank() }.joinToString("\n\n")
    val dishes = scans.flatMap { scan ->
        val pricedNames = scan.pricedDishes.map { it.name }.toSet()
        scan.pricedDishes + scan.dishes.filter { it !in pricedNames }.map { DraftDish(it, rawText = scan.rawText) }
    }.distinctBy { listOf(it.name, it.price, it.groupPrice, it.priceConditions, it.rawText) }
    return ImportDraft(
        shopName = scans.firstOrNull { it.shopName.isNotBlank() }?.shopName.orEmpty(),
        address = scans.firstOrNull { it.address.isNotBlank() }?.address.orEmpty(),
        source = source, sourceUrl = sourceUrl, platform = platform, rawText = raw,
        deals = scans.flatMap { scan -> scan.deals.map { d ->
            DraftDeal(d.title, d.currentPrice, d.originalPrice, d.soldText, d.tag, sourceUrl,
                rawText = d.rawText.ifBlank { scan.rawText }, conditionsText = d.conditionsText.ifBlank { priceConditionsOf(scan.rawText.lines()).let { if (it.isBlank()) "" else "页面条件（适用报价待核对）：\n$it" } },
                platform = importPlatform(d.platform, platform), sourceItemId = d.sourceItemId,
                observedAt = d.observedAt, priceConfirmed = false)
        } },
        dishes = dishes,
        notes = (scans.flatMap { it.notes } + if (raw.isNotBlank()) listOf("原识别文字默认不保存；若选择本机保留，请先去掉电话、核销码等无关信息，备份也会包含原文。") else emptyList()).distinct(),
    )
}

private fun validMoney(value: Double?) = value == null || (value.isFinite() && value >= 0.0)
private fun checkMoney(vararg values: Double?) {
    if (!values.all(::validMoney)) throw ImportValidationException("价格只能填写零或正数，不能是无效金额")
}

/** Replacements require a real, same-platform SKU and the exact snapshot shown in preview. */
internal fun replacementAllowed(old: Deal, incoming: DraftDeal, draft: ImportDraft): Boolean {
    val platform = importPlatform(incoming.platform.ifBlank { draft.platform }, draft.source)
    return platform in setOf("doushengsheng", "douyin", "meituan", "dianping", "eleme") &&
        platform == importPlatform(old.platform, old.source) &&
        incoming.sourceItemId.isNotBlank() && incoming.sourceItemId == old.sourceItemId
}

fun applyDraft(shops: List<Shop>, draft: ImportDraft, newId: () -> Long, now: Long): List<Shop> {
    val name = draft.shopName.trim()
    val existing = if (draft.matchedShopId != null) {
        shops.find { it.id == draft.matchedShopId }
            ?: throw ImportValidationException("原店铺已变化，请重新预览再导入")
    } else shops.find { name.isNotBlank() && normShopName(it.name) == normShopName(name) }
    val incomingDeals = draft.checkedDeals
    val incomingDishes = draft.checkedDishes
    incomingDeals.forEach {
        if (it.resolution != DealResolution.KEEP_EXISTING) {
            checkMoney(it.currentPrice, it.originalPrice)
            if (it.title.isBlank()) throw ImportValidationException("请先补上团购标题")
            if (it.observedAt != null && it.observedAt <= 0) throw ImportValidationException("观察时间无效，请留空或重新填写")
        }
    }
    incomingDishes.forEach {
        checkMoney(it.price, it.groupPrice)
        if (it.name.isBlank()) throw ImportValidationException("请先补上菜名")
    }
    var deals = existing?.deals.orEmpty()
    incomingDeals.forEach { d ->
        when (d.resolution) {
            DealResolution.KEEP_EXISTING -> Unit
            DealResolution.ADD -> deals = deals + d.toDeal(newId(), draft, now)
            DealResolution.REPLACE -> {
                val old = deals.find { it.id == d.existingId }
                if (old == null || old != d.expectedExisting || old.id != d.expectedExisting?.id)
                    throw ImportValidationException("旧报价已变化，请重新查看旧新差异后选择")
                if (!replacementAllowed(old, d, draft))
                    throw ImportValidationException("只有同平台且真实商品编号一致的报价才能替换；请另存新报价")
                // Replace the complete reviewed offer. Do not silently mix old conditions with new prices.
                deals = deals.map { if (it.id == old.id) d.toDeal(old.id, draft, now).copy(lastSeenAt = old.lastSeenAt) else it }
            }
        }
    }
    var dishes = existing?.dishes.orEmpty()
    incomingDishes.forEach { d ->
        val hit = dishes.find { it.name == d.savedName }
        if (hit == null) dishes = dishes + d.toDish(newId(), draft.retainRawText)
        else if (hit.price == null && hit.groupPrice == null && d.price != null)
            dishes = dishes.map { if (it.id == hit.id) d.toDish(hit.id, draft.retainRawText) else it }
    }
    if (existing == null) return shops + Shop(
        id = newId(), name = name.ifBlank { "新店铺" },
        emoji = draft.emoji.ifBlank { suggestEmoji(name) ?: "🍽️" }, dishes = dishes,
        address = draft.address.trim(), link = draft.sourceUrl, deals = deals,
    )
    val updated = existing.copy(name = name.ifBlank { existing.name }, emoji = existing.emoji.ifBlank { draft.emoji },
        address = draft.address.trim().ifBlank { existing.address }, dishes = dishes, deals = deals)
    return shops.map { if (it.id == existing.id) updated else it }
}

private fun DraftDish.toDish(id: Long, retainRawText: Boolean) = Dish(id, savedName, price, groupPrice, if (retainRawText) rawText else "", priceConditions, priceConfirmed)
private fun DraftDeal.toDeal(id: Long, draft: ImportDraft, now: Long) = Deal(
    id = id, title = title.trim(), currentPrice = currentPrice, originalPrice = originalPrice,
    soldText = soldText, tag = tag, source = draft.source, sourceUrl = sourceUrl,
    rawText = if (draft.retainRawText) rawText else "", conditionsText = conditionsText,
    platform = importPlatform(platform.ifBlank { draft.platform }, draft.source), sourceItemId = sourceItemId,
    importedAt = now, observedAt = observedAt, priceConfirmed = priceConfirmed,
)

fun missingDealsFor(shop: Shop?, draft: ImportDraft): List<String> {
    if (shop == null) return emptyList()
    return shop.deals.filter { old -> draft.deals.none { incoming ->
        replacementAllowed(old, incoming, draft)
    } }.map { it.title }
}

/** Matching suggests a comparison only. It never authorizes replacement. */
fun attachToShops(shops: List<Shop>, draft: ImportDraft): ImportDraft {
    val name = draft.shopName.trim()
    val shop = if (draft.matchedShopId != null) shops.find { it.id == draft.matchedShopId }
    else shops.find { name.isNotBlank() && normShopName(it.name) == normShopName(name) }
    if (shop == null) return draft
    return draft.copy(
        shopName = draft.shopName.ifBlank { shop.name }, matchedShopId = shop.id,
        emoji = draft.emoji.ifBlank { shop.emoji }, address = draft.address.ifBlank { shop.address },
        deals = draft.deals.map { d ->
            val hit = shop.deals.firstOrNull { replacementAllowed(it, d, draft) }
                ?: shop.deals.firstOrNull { normDealTitle(it.title) == normDealTitle(d.title) }
            d.copy(existingId = null, suspectedExistingId = hit?.id, expectedExisting = hit, resolution = DealResolution.ADD)
        },
        dishes = draft.dishes.map { d -> d.copy(existing = shop.dishes.any { it.name == d.savedName }) },
        missingDeals = missingDealsFor(shop, draft),
    )
}
