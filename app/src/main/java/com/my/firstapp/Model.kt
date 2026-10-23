package com.my.firstapp

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class Dish(
    val id: Long,
    val name: String,
    val price: Double?,
    val groupPrice: Double?,
    val rawText: String = "",
    val priceConditions: String = "",
    val priceConfirmed: Boolean = true,
) {
    /** 实际要花的钱：有团购价就按团购价算。 */
    val paid: Double? get() = if (priceConfirmed) groupPrice ?: price else null
}

data class Shop(
    val id: Long,
    val name: String,
    val emoji: String,
    val dishes: List<Dish>,
    /** 地址，用来打开地图导航 */
    val address: String = "",
    /** 团购页或地图分享链接（美团、大众点评、高德…） */
    val link: String = "",
    /** 应用私有照片目录里的文件名；旧存档没有此字段。 */
    val photo: String = "",
    /** 这家店的团购 / 代金券。**不参与转盘**，旧存档没有此字段。 */
    val deals: List<Deal> = emptyList(),
    /** Optional user-recorded estimate, not live navigation. */
    val walkMinutes: Int? = null,
)

/**
 * 团购 / 代金券 / 套餐。
 *
 * 它**不是**一道菜：「50 元代金券」是购买方式，不是「今天吃什么」。
 * 所以单独存一份，转盘只抽 [Dish]，结果页里最多提示「这家店有 N 个团购」。
 * 同名报价不代表同一商品；导入默认新增，明确对照确认后才能替换。
 */
data class Deal(
    val id: Long,
    val title: String,
    /** 当前团购价（页面上大号红色的那个） */
    val currentPrice: Double? = null,
    /** 划线原价 / 门市价，没有就是 null */
    val originalPrice: Double? = null,
    /** 「已售9000+」这类销量文案，只展示，不参与任何计算 */
    val soldText: String = "",
    /** 「5.8折热销中」这类促销标签，只展示 */
    val tag: String = "",
    /** 来源标记，例如 "doushengsheng"；手动加的为空 */
    val source: String = "",
    /** 只有真实分享链接才填；截图导入时留空，不伪造商品链接 */
    val sourceUrl: String = "",
    /** 历史字段：旧实现写入的是导入时刻，不能当作已确认的观察时间。 */
    val lastSeenAt: Long = 0L,
    val rawText: String = "",
    val conditionsText: String = "",
    val platform: String = "",
    val sourceItemId: String = "",
    val importedAt: Long = 0L,
    val observedAt: Long? = null,
    val priceConfirmed: Boolean = false,
)

data class Record(
    val time: Long,
    val shopEmoji: String,
    val shopName: String,
    val dishName: String,
    val price: Double?,
    val groupPrice: Double?,
) {
    val paid: Double get() = groupPrice ?: price ?: 0.0
    val saved: Double
        get() = if (price != null && groupPrice != null) (price - groupPrice).coerceAtLeast(0.0) else 0.0
}

data class Settings(
    val nickname: String = "",
    val theme: String = "strawberry",
    val sound: Boolean = true,
    /** 今日预算，null 表示不限 */
    val budget: Double? = null,
    /** 最近几天吃过的先不抽，0 表示不限制 */
    val avoidDays: Int = 2,
    /** 最近一次成功写入公共「下载/今天吃什么」自动备份的日期。 */
    val lastAutoBackupDay: String = "",
    val maxWalkMinutes: Int? = null,
    val allowUnknownPrice: Boolean = true,
    val allowUnknownWalk: Boolean = true,
)

/** 「今天不想吃」的店和菜，只在 day 这一天有效，第二天自动恢复。 */
data class SkipToday(
    val day: String = "",
    val shopIds: Set<Long> = emptySet(),
    val dishIds: Set<Long> = emptySet(),
)

data class AppData(
    val shops: List<Shop>,
    val history: List<Record>,
    val settings: Settings,
    val skip: SkipToday,
)

fun Double.yuan(): String =
    "¥" + String.format(Locale.US, "%.2f", this).trimEnd('0').trimEnd('.')

data class ParsedDish(val name: String, val price: Double?, val groupPrice: Double?)

private val TrailingPrice = Regex("""^(.*?)[\s,，、:：/|]*[¥￥]?(\d+(?:\.\d+)?)\s*(?:元|块)?$""")

/**
 * 批量粘贴的菜单，一行一样，比如「招牌麻辣烫 25 19.9」。
 * 只把行尾的数字当价格（最多两个），所以「2号套餐 30」也能认对；两个价格时大的是原价、小的是团购价。
 */
fun parseDishLines(text: String): List<ParsedDish> = text.lines().mapNotNull { line ->
    var rest = line.trim().trimStart('-', '•', '·', '*').trim()
    val prices = mutableListOf<Double>()
    while (prices.size < 2) {
        val m = TrailingPrice.find(rest) ?: break
        if (m.groupValues[1].isBlank()) break
        prices.add(0, m.groupValues[2].toDouble())
        rest = m.groupValues[1].trim()
    }
    // 空行、只有价格没有名字的行都跳过
    if (rest.isBlank() || rest.trimStart('¥', '￥').toDoubleOrNull() != null) return@mapNotNull null
    when (prices.size) {
        0 -> ParsedDish(rest, null, null)
        1 -> ParsedDish(rest, prices[0], null)
        else -> {
            val hi = prices.max()
            val lo = prices.min()
            ParsedDish(rest, hi, if (lo < hi) lo else null)
        }
    }
}

/** 所有数据存成一个 JSON 文件，备份和分享也用同一个格式。 */
object DataJson {
    fun encode(data: AppData): String = JSONObject()
        .put("version", 2)
        .put("shops", JSONArray(data.shops.map { shop ->
            JSONObject()
                .put("id", shop.id)
                .put("name", shop.name)
                .put("emoji", shop.emoji)
                .put("address", shop.address)
                .put("link", shop.link)
                .put("photo", shop.photo)
                .put("walkMinutes", shop.walkMinutes ?: JSONObject.NULL)
                .put("dishes", JSONArray(shop.dishes.map { dish ->
                    JSONObject()
                        .put("id", dish.id)
                        .put("name", dish.name)
                        .putPrice("price", dish.price)
                        .putPrice("groupPrice", dish.groupPrice)
                        .put("rawText", dish.rawText)
                        .put("priceConditions", dish.priceConditions)
                        .put("priceConfirmed", dish.priceConfirmed)
                }))
                .put("deals", JSONArray(shop.deals.map { deal ->
                    JSONObject()
                        .put("id", deal.id)
                        .put("title", deal.title)
                        .putPrice("currentPrice", deal.currentPrice)
                        .putPrice("originalPrice", deal.originalPrice)
                        .put("soldText", deal.soldText)
                        .put("tag", deal.tag)
                        .put("source", deal.source)
                        .put("sourceUrl", deal.sourceUrl)
                        .put("lastSeenAt", deal.lastSeenAt)
                        .put("rawText", deal.rawText)
                        .put("conditionsText", deal.conditionsText)
                        .put("platform", deal.platform)
                        .put("sourceItemId", deal.sourceItemId)
                        .put("importedAt", deal.importedAt)
                        .put("observedAt", deal.observedAt ?: JSONObject.NULL)
                        .put("priceConfirmed", deal.priceConfirmed)
                }))
        }))
        .put("history", JSONArray(data.history.map { r ->
            JSONObject()
                .put("time", r.time)
                .put("shopEmoji", r.shopEmoji)
                .put("shopName", r.shopName)
                .put("dishName", r.dishName)
                .putPrice("price", r.price)
                .putPrice("groupPrice", r.groupPrice)
        }))
        .put("settings", JSONObject()
            .put("nickname", data.settings.nickname)
            .put("theme", data.settings.theme)
            .put("sound", data.settings.sound)
            .putPrice("budget", data.settings.budget)
            .put("avoidDays", data.settings.avoidDays)
            .put("maxWalkMinutes", data.settings.maxWalkMinutes ?: JSONObject.NULL)
            .put("allowUnknownPrice", data.settings.allowUnknownPrice)
            .put("allowUnknownWalk", data.settings.allowUnknownWalk)
            .put("lastAutoBackupDay", data.settings.lastAutoBackupDay))
        .put("skip", JSONObject()
            .put("day", data.skip.day)
            .put("shopIds", JSONArray(data.skip.shopIds.toList()))
            .put("dishIds", JSONArray(data.skip.dishIds.toList())))
        .toString()

    fun decode(text: String): AppData {
        val root = JSONObject(text)
        val shops = root.getJSONArray("shops").objects().map { s ->
            Shop(
                id = s.getLong("id"),
                name = s.getString("name"),
                emoji = s.optString("emoji", "🍽️"),
                address = s.optString("address", ""),
                link = s.optString("link", ""),
                photo = s.optString("photo", ""),
                walkMinutes = s.optMinutes("walkMinutes"),
                dishes = s.optionalArray("dishes").objects().map { d ->
                    Dish(d.getLong("id"), d.getString("name"), d.optPrice("price"), d.optPrice("groupPrice"),
                        rawText = d.optString("rawText", ""), priceConditions = d.optString("priceConditions", ""),
                        priceConfirmed = d.optBoolean("priceConfirmed", true))
                },
                // 旧存档没有 deals 字段，这里必须容错成空列表。
                deals = s.optionalArray("deals").objects().map { d ->
                    Deal(
                        id = d.getLong("id"),
                        title = d.getString("title"),
                        currentPrice = d.optPrice("currentPrice"),
                        originalPrice = d.optPrice("originalPrice"),
                        soldText = d.optString("soldText", ""),
                        tag = d.optString("tag", ""),
                        source = d.optString("source", ""),
                        sourceUrl = d.optString("sourceUrl", ""),
                        lastSeenAt = d.optLong("lastSeenAt", 0L),
                        rawText = d.optString("rawText", ""), conditionsText = d.optString("conditionsText", ""),
                        platform = d.optString("platform", ""), sourceItemId = d.optString("sourceItemId", ""),
                        importedAt = d.optLong("importedAt", 0L),
                        observedAt = if (d.isNull("observedAt")) null else d.optLong("observedAt").takeIf { it > 0 },
                        priceConfirmed = d.optBoolean("priceConfirmed", false),
                    )
                },
            )
        }
        val history = root.getJSONArray("history").objects().map { r ->
            Record(
                time = r.getLong("time"),
                shopEmoji = r.optString("shopEmoji", "🍽️"),
                shopName = r.getString("shopName"),
                dishName = r.getString("dishName"),
                price = r.optPrice("price"),
                groupPrice = r.optPrice("groupPrice"),
            )
        }
        val s = root.optionalObject("settings")
        val settings = if (s == null) Settings() else Settings(
            nickname = s.optString("nickname", ""),
            theme = s.optString("theme", "strawberry"),
            sound = s.optBoolean("sound", true),
            budget = s.optPrice("budget"),
            avoidDays = s.optInt("avoidDays", 2),
            lastAutoBackupDay = s.optString("lastAutoBackupDay", ""),
            maxWalkMinutes = s.optMinutes("maxWalkMinutes"),
            allowUnknownPrice = s.optBoolean("allowUnknownPrice", true),
            allowUnknownWalk = s.optBoolean("allowUnknownWalk", true),
        )
        val k = root.optionalObject("skip")
        val skip = if (k == null) SkipToday() else SkipToday(
            day = k.optString("day", ""),
            shopIds = k.optionalArray("shopIds").longs().toSet(),
            dishIds = k.optionalArray("dishIds").longs().toSet(),
        )
        return AppData(shops, history, settings, skip)
    }

    /** 从一段文字里抠出 JSON（分享的文字前面带了说明）。 */
    fun extract(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start >= 0 && end > start) { "没有找到菜单数据" }
        return text.substring(start, end + 1)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else List(length()) { getJSONObject(it) }

    private fun JSONArray?.longs(): List<Long> =
        if (this == null) emptyList() else List(length()) { getLong(it) }

    private fun JSONObject.optionalArray(key: String): JSONArray? =
        if (has(key)) getJSONArray(key) else null

    private fun JSONObject.optionalObject(key: String): JSONObject? =
        if (has(key)) getJSONObject(key) else null

    private fun JSONObject.putPrice(key: String, value: Double?): JSONObject =
        if (value != null) put(key, value) else this

    private fun JSONObject.optPrice(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = getDouble(key)
        require(value.isFinite() && value >= 0.0) { "Invalid amount: $key" }
        return value
    }
}


/**
 * 从各家店的吃的里随机挑 count 样：轮流从不同的店里拿，尽量让店不重样，最后再打乱顺序。
 * 「直接抽吃的」用它挑 7 样上转盘。
 */
fun pickVariety(groups: List<Pair<Shop, List<Dish>>>, count: Int, random: kotlin.random.Random): List<Pair<Shop, Dish>> {
    val queues = groups
        .filter { it.second.isNotEmpty() }
        .map { (shop, dishes) -> ArrayDeque(dishes.shuffled(random).map { shop to it }) }
        .shuffled(random)
        .toMutableList()
    val result = mutableListOf<Pair<Shop, Dish>>()
    while (result.size < count && queues.isNotEmpty()) {
        val iter = queues.iterator()
        while (iter.hasNext() && result.size < count) {
            val q = iter.next()
            result += q.removeFirst()
            if (q.isEmpty()) iter.remove()
        }
    }
    return result.shuffled(random)
}


private fun JSONObject.optMinutes(key: String): Int? {
    val value = opt(key) as? Number ?: return null
    val minutes = value.toDouble()
    return minutes.takeIf { it.isFinite() && it in 0.0..240.0 && it % 1.0 == 0.0 }?.toInt()
}
