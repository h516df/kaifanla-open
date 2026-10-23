package com.my.firstapp

/**
 * 一张截图 / 一段分享扫出来的结构化结果。
 *
 * 「整页 OCR → 一堆文本」是不够用的：抖省省的店铺页和团购页里，
 * 店名、地址、团购标题、当前价、划线原价、销量、折扣标签混在一起，
 * 直接抄下来会得到一堆没法用的行。这里按页面分区整理。
 */
data class PageScan(
    val shopName: String = "",
    val address: String = "",
    val deals: List<ScannedDeal> = emptyList(),
    val dishes: List<String> = emptyList(),
    /** 识别不确定的地方，交给预览页提示用户改，不静默乱猜。 */
    val notes: List<String> = emptyList(),
    val rawText: String = "",
    val pricedDishes: List<DraftDish> = emptyList(),
) {
    val isEmpty: Boolean get() = shopName.isBlank() && address.isBlank() && deals.isEmpty() && dishes.isEmpty()
}

/** 一张卡片上认出来的一条团购。 */
data class ScannedDeal(
    val title: String,
    val currentPrice: Double?,
    val originalPrice: Double?,
    val soldText: String = "",
    val tag: String = "",
    val rawText: String = "",
    val conditionsText: String = "",
    val platform: String = "",
    val sourceItemId: String = "",
    val observedAt: Long? = null,
)

private val SoldWords = Regex("""(已售|月售|已抢|售出|人已购|人购买|好评|销量|回头客|热销|抢购|消费人数)""")
private val DiscountToken = Regex("""\d+(?:\.\d+)?\s*折""")
private val DiscountTag = Regex("""(?<![\d.])0?(\d(?:\.\d{1,2})?)\s*折""")
private val DistanceOnly = Regex("""^\s*\d+(?:\.\d+)?\s*(?:米|m|km|公里)\s*$""", RegexOption.IGNORE_CASE)
private val TrailingCount = Regex("""[\s·・|]*(?:\d+\s*(?:人\s*(?:推荐|推|收藏|购买|已购)|(?:推荐|收藏|购买|已购))|推荐\s*\d+|月售\s*\d+.*)$""")
private val UrlInText = Regex("""https?://[^\s，。、）)】"']+""")
private val CjkOrLetter = Regex("""[\u3400-\u4dbf\u4e00-\u9fffA-Za-z]""")

/** 状态栏的时间 / 电量 / 信号 / 图标：不管在哪都丢掉。 */
private val SystemStrong = Regex(
    """^\W*(?:\d{1,2}[:：]\d{2}(?::\d{2})?|\d{1,3}\s*%|\d{1,2}\s*[Gg5]|[Q☆★▲▼●○])\W*$""",
)

/** 光秃秃的 3~4 位数字（电量 924 这种）。只在页面最顶上才当系统信息，菜单里的价格不能被误杀。 */
private val SystemWeak = Regex("""^\W*\d{3,4}\W*$""")

/** 店名常见的结尾。截图顶部常有宣传语，靠这个把真店名挑出来。 */
private val ShopNameTail = listOf(
    "餐厅", "饭店", "酒楼", "酒家", "面馆", "粉店", "小吃", "食堂", "火锅", "香锅", "麻辣烫",
    "米线", "烤肉", "烧烤", "奶茶", "咖啡", "甜品", "烘焙", "超市", "便利店", "披萨", "汉堡",
    "馆", "店", "城", "铺", "庄", "苑", "居",
)

/** 带 ¥ 的数字才是价格。真实抖省省页面里价格一定带 ¥。 */
private val YenNumber = Regex("""[¥￥]\s*(\d{1,6}(?:\.\d{1,2})?)(?![\d.eE:：+\-])""")

/** ML Kit 常把 ¥ 认成 Y/y（`Y9.9`），先归一化再抠价格。 */
private val YenMisread = Regex("""(?<![A-Za-z])[Yy](?=\d)""")

/** 不带 ¥ 的裸数字，只给「普通菜单照片」那条路用。 */
private val BareNumber = Regex("""(?<![\d.A-Za-z:：+\-])(\d{1,6}(?:\.\d{1,2})?)(?![\d.A-Za-z:：+\-])""")

/**
 * 抖省省把「现价 ￥5.9」「划线原价 ￥7」「8.5折」粘成了一行 `￥5.9￥78.5折`。
 * 光看数字分不出 7 和 78.5，但折扣能帮忙：正则回溯会把 `78.5折` 拆成 `7` + `8.5折`。
 * 真实样本：`￥210￥2388.9折` → 210 / 238 / 8.9折。
 */
private val GluedTwoPrices = Regex(
    """[¥￥]\s*(\d{1,6}(?:\.\d{1,2})?)\s*[¥￥]\s*(\d{1,6}(?:\.\d{1,2})?)\s*(\d(?:\.\d{1,2})?)\s*折""",
)

/** 只有一个价格被折扣粘住：`￥3089.1折` → 308 + 9.1折。 */
private val GluedOnePrice = Regex("""[¥￥]\s*(\d{1,6}(?:\.\d{1,2})?)(\d(?:\.\d{1,2})?)\s*折""")

/** 这些是促销词，出现在价格行里不算「别的字」。 */
private val PromoWords = Regex(
    """(首单抄底价|抄底价|超省价|神鲜价|热销中|热销|已售|抢购|抢|补贴|券后|会员价|折后|首单|低至|元起|省|起)""",
)

private val SectionWords = listOf(
    "用户推荐菜", "网友推荐", "推荐菜", "招牌菜", "本店特色", "大家喜欢", "热销推荐", "进店必买",
    "优惠团购", "团购套餐", "团购", "优惠", "套餐", "代金券", "商家信息", "商家", "店铺信息",
    "地址", "简介", "营业时间", "评价", "附近", "全部", "更多", "查看全部", "猜你喜欢", "到店团购",
)

private val AddressHints = listOf(
    "省", "市", "区", "县", "镇", "乡", "街道", "大道", "路", "街", "巷", "号", "栋", "幢",
    "广场", "大厦", "中心", "小区", "商业街", "名店街", "村", "组", "楼", "室",
)

private fun String.norm(): String = replace('\u3000', ' ').replace(Regex("\\s+"), " ").trim()

/**
 * 这一行认出来的所有价格。
 *
 * 只有「整行除了价格、折扣、促销词之外没有别的字」才算价格行，
 * 这样「【示例·菜品20元自选】」这种带数字的标题不会被误当价格。
 */
fun pricesInLine(text: String): List<Double> {
    val raw = text.norm()
    if (raw.isBlank()) return emptyList()
    if (SystemStrong.matches(raw)) return emptyList()
    if (DistanceOnly.matches(raw)) return emptyList()
    val t = YenMisread.replace(raw, "¥")
    val hasYen = t.contains('¥') || t.contains('￥')
    // 「已售9000+」这种没有 ¥ 的销量行直接排除，别把 9000 当价格
    if (!hasYen && SoldWords.containsMatchIn(t)) return emptyList()

    val yen = YenNumber.findAll(t).map { it.groupValues[1].toDouble() }.toList()
    var rest = if (hasYen) YenNumber.replace(t, " ") else t
    rest = rest.replace(DiscountToken, " ")
    rest = PromoWords.replace(rest, " ")
    rest = Regex("""\d{1,6}(?:\.\d{1,2})?\s*(?:元|块)?""").replace(rest, " ")
    // 只要剩下的不是中文词就算价格行：OCR 常把 ¥ 认成 l / Y / | 之类的零碎符号，
    // 用「还剩几个字符」当判据会把真实价格行全丢掉。
    if (rest.any { it in '\u4e00'..'\u9fff' }) return emptyList()
    if (hasYen) return yen
    // 没有 ¥ 的行（菜单照片常见）才把裸数字当价格；折扣里的数字（5.8折）不算
    return BareNumber.findAll(DiscountToken.replace(t, " ")).map { it.groupValues[1].toDouble() }.toList()
}

/** 旧接口：整行就是一个价格时返回它。 */
fun priceValueOf(text: String): Double? = pricesInLine(text).singleOrNull()

/** 这一行里的折扣标签，例如 `8.5折`。 */
fun discountInLine(text: String): String = DiscountTag.find(text.norm())?.let { "${it.groupValues[1]}折" }.orEmpty()

private fun isSoldLine(t: String) = SoldWords.containsMatchIn(t)

/** 「5.8折」「5.8折热销中」这种短标签；长标题里带折扣字样的不算。 */
private fun isDiscountLine(t: String) = DiscountToken.containsMatchIn(t) && t.length <= 14 && pricesInLine(t).isEmpty()

private fun isSectionHeader(t: String): Boolean {
    val bare = t.norm().trim('·', '・', '|', '-', '—').trim()
    return SectionWords.any { bare == it || bare.startsWith("$it ") || bare.startsWith("$it(") || bare.startsWith("$it（") }
}

private fun looksLikeAddress(t: String): Boolean {
    if (t.length < 6 || t.length > 60) return false
    if (pricesInLine(t).isNotEmpty() || isSoldLine(t) || DistanceOnly.matches(t)) return false
    val hits = AddressHints.count { t.contains(it) }
    if (hits == 0) return false
    return t.any { it.isDigit() } || hits >= 2
}

private fun looksLikeShopName(t: String): Boolean {
    if (t.length !in 3..26) return false
    if (!CjkOrLetter.containsMatchIn(t)) return false
    if (t.contains('¥') || t.contains('￥')) return false
    if (pricesInLine(t).isNotEmpty() || isSoldLine(t) || DistanceOnly.matches(t)) return false
    if (isSectionHeader(t) || isPriceCondition(t)) return false
    return true
}

/**
 * 抖省省页面 → 结构化结果。
 *
 * 做法（全部基于文字块 + 坐标，不依赖字号样式）：
 * 1. 先把价格行、销量行、折扣行、距离行、区块标题行挑出来；
 * 2. 店名取页面顶部框最高的那一行（大标题通常框也高）；
 * 3. 地址取最长的「像地址」的行；
 * 4. 推荐菜区块标题之后的菜名当菜品候选；
 * 5. 剩下的行按纵向间距切成卡片，有价格的卡片就是一条团购；
 *    卡片里框最高的价格是当前价，另一个是划线原价。
 */
fun scanDoushengsheng(lines: List<OcrLine>): PageScan {
    val normalized = lines.mapNotNull { line ->
        val text = line.text.norm()
        if (text.isEmpty()) null else line.copy(text = text)
    }
    if (normalized.isEmpty()) return PageScan()

    // 有坐标的行才拿来判断「是不是在页面最顶上」
    val boxed = normalized.filter { it.right > it.left && it.bottom > it.top }
    val statusZone = if (boxed.size * 2 >= normalized.size) {
        val top = boxed.minOf { it.top }
        val bottom = boxed.maxOf { it.bottom }.coerceAtLeast(top + 1)
        top + (bottom - top) * 6 / 100
    } else {
        Int.MIN_VALUE
    }
    val cleaned = normalized.filter { line ->
        !SystemStrong.matches(line.text) && !(line.top <= statusZone && SystemWeak.matches(line.text))
    }
    if (cleaned.isEmpty()) return PageScan()

    val notes = mutableListOf<String>()
    val sorted = cleaned.sortedBy { it.top }
    val pageTop = sorted.first().top
    val pageBottom = sorted.maxOf { it.bottom }.coerceAtLeast(pageTop + 1)
    val topZone = pageTop + (pageBottom - pageTop) * 3 / 10
    // 状态栏已经按 SystemStrong / SystemWeak 丢掉了，这里不再按比例裁顶部，
    // 否则矮一点的页面（比如测试里的模拟坐标）会把店名一起裁掉。
    val content = sorted

    // 1. 店名：优先「带店名常见结尾」的那一行（顶部常有宣传语，比如「肉香与菜鲜共舞」）
    // 团购/推荐菜区块内的标题与乱码价格不属于店铺身份。
    val identityEnd = content.firstOrNull {
        isSectionHeader(it.text) && (it.text.contains("团购") || isDishSection(it.text))
    }?.top ?: Int.MAX_VALUE
    val nameCandidates = content.filter {
        it.top <= topZone && it.top < identityEnd && looksLikeShopName(it.text) && !looksLikeAddress(it.text)
    }
    val shopName = (
        nameCandidates.filter { line -> ShopNameTail.any { line.text.endsWith(it) } }.maxByOrNull { it.height }
            ?: nameCandidates.maxByOrNull { it.height }
        )?.text.orEmpty()

    // 2. 地址：剩下行里最长的「像地址」的行
    val address = content
        .filter { it.text != shopName && looksLikeAddress(it.text) }
        .maxByOrNull { it.text.length }
        ?.text
        .orEmpty()

    // 3. 推荐菜区块
    val headerIndex = content.indexOfFirst { isSectionHeader(it.text) && isDishSection(it.text) }
    val dishLines = if (headerIndex >= 0) {
        content.drop(headerIndex + 1)
            .takeWhile { !isSectionHeader(it.text) }
            .mapNotNull { dishNameOf(it.text) }
    } else {
        emptyList()
    }
    val dishZoneTop = if (headerIndex >= 0) content[headerIndex].top else Int.MAX_VALUE

    // 4. 剩下的行按纵向间距切成卡片
    val rest = content.filter { line ->
        line.text != shopName &&
            line.text != address &&
            !isSectionHeader(line.text) &&
            line.top < dishZoneTop
    }
    val cards = clusterCards(rest)
    // 只有整页里出现过「团购证据」的页面才按团购处理：划线原价 / 销量 / 折扣标签 / 团购关键词。
    // 普通菜单照片只有「菜名 + 单价」，一个证据都没有，就该走菜品那条路，
    // 否则每道菜都会被误判成一张团购券。
    val deals = if (cards.any { hasDealEvidence(it) }) cards.mapNotNull { card -> cardToDeal(card, notes) } else emptyList()

    if (deals.isEmpty() && dishLines.isEmpty() && shopName.isBlank() && address.isBlank()) {
        notes += "这张图没认出店铺或团购，换一张清楚点的截图试试～"
    }
    return PageScan(shopName = shopName, address = address, deals = deals, dishes = dishLines, notes = notes,
        rawText = cleaned.joinToString("\n") { it.text })
}

private fun isDishSection(text: String): Boolean {
    val t = text.norm()
    return listOf("用户推荐菜", "网友推荐", "推荐菜", "招牌菜", "本店特色", "大家喜欢", "热销推荐", "进店必买")
        .any { t.startsWith(it) }
}

private fun dishNameOf(text: String): String? {
    var t = text.norm().trim('·', '・', '|', '-', '—', '*').trim()
    t = TrailingCount.replace(t, "").trim()
    if (t.length !in 2..20) return null
    if (!CjkOrLetter.containsMatchIn(t)) return null
    if (pricesInLine(t).isNotEmpty() || isSoldLine(t) || isDiscountLine(t)) return null
    if (isSectionHeader(t) || isPriceCondition(t)) return null
    return t
}

/** 纵向间距大于 1.5 倍中位行高就认为换了一张卡片。 */
private fun clusterCards(lines: List<OcrLine>): List<List<OcrLine>> {
    if (lines.isEmpty()) return emptyList()
    val sorted = lines.sortedBy { it.top }
    val heights = sorted.map { it.height }.sorted()
    val median = heights[heights.size / 2].coerceAtLeast(8)
    val threshold = median * 3 / 2
    val cards = mutableListOf<MutableList<OcrLine>>()
    var lastBottom: Int? = null
    sorted.forEach { line ->
        val previous = lastBottom
        if (previous == null || line.top - previous > threshold) cards.add(mutableListOf(line))
        else cards.last().add(line)
        lastBottom = maxOf(previous ?: line.bottom, line.bottom)
    }
    return cards
}

/** 团购标题里的常见词。有这些词说明这张卡片是券/套餐，不是一道菜。 */
private val DealWords = listOf(
    "券", "套餐", "代金", "团购", "自选", "福利", "超值", "双人餐", "单人餐", "人餐",
    "必点", "拼团", "秒杀", "抢购", "立减", "折扣", "特惠", "一元", "任选", "份餐", "现价", "原价",
)

/** 乱码金额不能截出一个看似合法的前缀（如 Y6e-06 → 6），保留卡片供用户核对。 */
private fun hasUnclearPriceLine(text: String): Boolean {
    val t = YenMisread.replace(text.norm(), "¥")
    val malformedNumber = t.replace('¥', ' ').replace('￥', ' ').split(Regex("""\s+"""))
        .any { token ->
            Regex("""\d[\d.eE:：+\-]*""").matches(token) &&
                !Regex("""\d{1,6}(?:\.\d{1,2})?""").matches(token)
        }
    return malformedNumber ||
        ((t.contains('¥') || t.contains('￥')) && pricesInLine(t).isEmpty() && DealWords.none { t.contains(it) })
}
/**
 * 这张卡片有没有「团购证据」：划线原价 / 销量 / 折扣标签 / 团购关键词。
 * 一个都没有的，就是「菜名 + 单价」，不能当团购券。
 */
private fun hasDealEvidence(card: List<OcrLine>): Boolean {
    if (card.any { pricesInLine(it.text).size >= 2 }) return true
    if (card.any { isSoldLine(it.text) }) return true
    if (card.any { isDiscountLine(it.text) }) return true
    val words = card.filter { pricesInLine(it.text).isEmpty() }.joinToString(" ") { it.text }
    return DealWords.any { words.contains(it) }
}

private fun cardToDeal(card: List<OcrLine>, notes: MutableList<String>): ScannedDeal? {
    // 店铺人均价/营业信息不能因乱码保留成团购，卡片自身也必须有团购证据。
    val keepUnclear = hasDealEvidence(card)
    val priceLines = card.filter {
        pricesInLine(it.text).isNotEmpty() || GluedOnePrice.containsMatchIn(it.text) ||
            (keepUnclear && hasUnclearPriceLine(it.text))
    }
    if (priceLines.isEmpty()) return null

    val title = card
        .filter { pricesInLine(it.text).isEmpty() && !hasUnclearPriceLine(it.text) && !isSoldLine(it.text) && !isDiscountLine(it.text) && !isPriceCondition(it.text) }
        .maxByOrNull { it.text.length }
        ?.text
        .orEmpty()

    var current: Double? = null
    var original: Double? = null
    // 折扣标签可能粘在价格行里（`¥5.9¥7l8.5折热销中`），也可能单独一行（`07.1折热銷中`）
    var tag = card.firstNotNullOfOrNull { line -> discountInLine(line.text).ifBlank { null } }.orEmpty()

    // 优先用「现价 + 原价 + 折扣」粘在一起的那一行，折扣能帮正则把数字拆对
    val gluedTwo = card.firstNotNullOfOrNull { GluedTwoPrices.find(it.text) }
    val gluedOne = if (gluedTwo == null) card.firstNotNullOfOrNull { GluedOnePrice.find(it.text) } else null
    when {
        gluedTwo != null -> {
            current = gluedTwo.groupValues[1].toDoubleOrNull()
            original = gluedTwo.groupValues[2].toDoubleOrNull()
            tag = "${gluedTwo.groupValues[3]}折"
        }
        gluedOne != null -> {
            // `￥3089.1折` 里的 308 是划线原价，现价在卡片里另外一行（`￥280`）
            val discounted = gluedOne.groupValues[1].toDoubleOrNull()
            tag = "${gluedOne.groupValues[2]}折"
            val other = priceLines
                .filter { GluedOnePrice.find(it.text) == null }
                .flatMap { pricesInLine(it.text) }
                .maxOrNull()
            if (other != null) {
                current = other
                original = discounted
            } else {
                current = discounted
            }
        }
        else -> {
            val withHeight = priceLines
                .flatMap { line -> pricesInLine(line.text).map { it to line.height } }
                .sortedByDescending { it.second }
            current = withHeight.getOrNull(0)?.first
            original = withHeight.getOrNull(1)?.first
        }
    }

    if (current != null && original != null && current > original) {
        notes += "「${title.ifBlank { "有条团购" }}」的价格角色不确定，未按大小交换，请对照原文确认。"
    }
    if (card.any { hasUnclearPriceLine(it.text) }) {
        notes += "「${title.ifBlank { "有条团购" }}」有金额没认清，已留空，不会把乱码拆成价格，请对照截图补上。"
    }
    if (title.isBlank()) notes += "有一张卡片只认出价格（${current?.yuan() ?: "?"}），名字没认出来，请在预览里补上。"

    return ScannedDeal(
        title = title,
        currentPrice = current,
        originalPrice = original,
        soldText = card.firstOrNull { isSoldLine(it.text) }?.text.orEmpty(),
        tag = tag,
        rawText = card.joinToString("\n") { it.text },
        conditionsText = priceConditionsOf(card.map { it.text }),
    )
}

/**
 * 导入用的一站式扫描：
 * 先按抖省省的结构化规则扫；如果团购和推荐菜都没扫出来（比如是一张普通菜单照片），
 * 退回 #7 已有的「按行拼菜名+价格」逻辑，保证老能力不丢。
 */
fun scanForImport(lines: List<OcrLine>): PageScan {
    val structured = scanDoushengsheng(lines)
    if (structured.deals.isNotEmpty() || structured.dishes.isNotEmpty()) return structured
    val dishes = ocrLinesToDraftDishes(lines)
    if (dishes.isEmpty()) return structured.copy(rawText = lines.joinToString("\n") { it.text })
    return structured.copy(dishes = dishes.map { it.name }, pricedDishes = dishes,
        rawText = lines.joinToString("\n") { it.text },
        notes = structured.notes.filterNot { it.startsWith("这张图没认出") })
}

/** 分享文本里能拿到的信息。抖省省可能分享纯文本或链接。 */
data class SharedText(val url: String, val text: String)

fun parseSharedText(raw: String): SharedText {
    val text = raw.norm()
    val url = UrlInText.find(text)?.value.orEmpty()
    return SharedText(url = url, text = text)
}

