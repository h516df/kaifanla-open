package com.my.firstapp

/**
 * OCR 认出来的一行文字，以及它在截图里的位置（像素）。
 *
 * 位置是用来「拼行」的：团购页经常把菜名放在左边、价格放在右边，
 * OCR 会当成两行。同一水平线上的行要先拼回一行，`parseDishLines` 才认得出来。
 */
data class OcrLine(
    val text: String,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    val height: Int get() = (bottom - top).coerceAtLeast(1)
    val centerY: Int get() = (top + bottom) / 2
}

/**
 * 团购页上这些词是价格标签、销量、按钮文字，不是菜名。
 * 拼好行之后先去掉，免得 `parseDishLines` 把它们当成一道菜（比如「门市价 268」）。
 * 长的词排前面，避免「抢购价」被「抢购」先切掉一半。
 */
private val MenuNoiseWords = listOf(
    "门市价", "原价", "现价", "团购价", "券后价", "抢购价", "折后价", "市场价", "参考价",
    "特价", "会员价", "到手价", "秒杀价", "活动价",
    "已售出", "已售", "月售", "销量", "已抢", "售出",
    "立即抢购", "立即购买", "马上抢", "去抢购", "立即预订",
    "领优惠券", "优惠券", "领券", "满减", "返现",
    "有效期", "使用时间", "不可用日期", "购买须知", "温馨提示", "退改规则", "随时退", "过期退", "免预约",
    "查看全部", "图文详情", "全部套餐", "收藏", "分享", "展开", "收起",
    "评分", "人均", "评价",
).sortedByDescending { it.length }

/** 行里至少要有中文或字母，才算「可能是一道菜」。 */
private val HasWord = Regex("[\\u3400-\\u4dbf\\u4e00-\\u9fffA-Za-z]")

/** 整行只有价格，比如「¥268」「268元」「¥268 ¥198」。 */
private val PriceOnlyRow = Regex("""^(?:[¥￥]?\d{1,5}(?:\.\d{1,2})?\s*(?:元|块)?[\s,，、/|·]*)+$""")

/**
 * 把 OCR 的行整理成现有「批量粘贴菜单」认的格式：一行一样，菜名后面跟价格。
 *
 * 做三件事：
 * 1. 同一水平线上的文字按左到右拼成一行（菜名 + 价格）；
 * 2. 去掉「门市价」「已售」这类噪声词；
 * 3. 团购页常把「菜名 / 门市价 / 抢购价」竖着排三行，这里把只有价格的行贴回上一条菜名，
 *    交给 `parseDishLines` 判大小：大的算原价、小的算团购价。
 *
 * 识别不可能 100% 正确，所以结果只是「预填」，用户还能在预览里改和删。
 */
fun ocrLinesToMenuText(lines: List<OcrLine>): String {
    val cleaned = lines.mapNotNull { line ->
        val text = line.text.replace('\u3000', ' ').replace(Whitespace, " ").trim()
        if (text.isEmpty()) null else line.copy(text = text)
    }
    if (cleaned.isEmpty()) return ""

    // 拿不到位置信息时（个别识别结果没有边框），就按识别顺序每行各算一行，至少不丢字。
    val rows = if (cleaned.all { it.right > it.left && it.bottom > it.top }) {
        groupOcrRows(cleaned)
    } else {
        cleaned.map { listOf(it) }
    }

    val joined = rows
        .map { row -> row.sortedBy { it.left }.joinToString(" ") { it.text }.replace(Whitespace, " ").trim() }
        .map { stripNoiseWords(it) }
        .filter { it.isNotEmpty() }
        .flatMap { splitMergedRow(it) }

    val dishes = mutableListOf<String>()
    joined.forEach { row ->
        val prev = dishes.lastOrNull()
        if (prev != null && PriceOnlyRow.matches(row) && dishPriceCount(prev) < 2) {
            dishes[dishes.size - 1] = "$prev $row"
        } else if (HasWord.containsMatchIn(row)) {
            dishes.add(row)
        }
    }

    // 菜单照片顶上常有「本店菜单」「XX 餐厅」这类标题，它们排在所有菜之前、也不带价格。
    // 只丢这一种：第一个有价格的行之前、且自己一个价格都没有的行。
    // 后面的行即使没写价格也留着（可能是「时价」），全都无价格时也一个都不丢。
    val firstPriced = dishes.indexOfFirst { dishPriceCount(it) > 0 }
    val start = if (firstPriced > 0) firstPriced else 0
    return dishes.drop(start).joinToString("\n")
}

private val Whitespace = Regex("\\s+")

/** 单独一个词就是价格：`25`、`19.9`、`¥268`、`16元`。 */
private val PriceToken = Regex("""^[¥￥]?\d{1,5}(?:\.\d{1,2})?\s*(?:元|块)?$""")

/** 词尾粘着价格，比如 OCR 把「凉拌皮蛋12元」连成一个词。 */
private val TrailingPrice = Regex("""^(.+?)([¥￥]?\d{1,5}(?:\.\d{1,2})?\s*(?:元|块)?)$""")

/**
 * 一行里挤了好几道菜时拆开。
 *
 * 真实的饿了么/美团菜单页是并排 2~4 栏，OCR 按「同一水平线算一行」拼完，
 * 一道菜的名字里会混进隔壁菜的名字和价格；印刷菜单的两栏也常把
 * 一串菜名和一串价格排在同一行。这两种都不拆就没法用。
 *
 * 拆法：
 * - 先一串菜名、再一串价格，数量相等 → 一一配上（印刷菜单两栏）；
 * - 「菜名 价格 菜名 价格 …」→ 每个价格配它前面那几个词（App 菜单列表）；
 * - 光有价格没名字 → 贴回上一道，交给 `parseDishLines` 判原价/团购价。
 */
private fun splitMergedRow(row: String): List<String> {
    val tokens = tokenizeRow(row)
    val isPrice = tokens.map { PriceToken.matches(it) }
    if (isPrice.count { it } < 2) return listOf(row)

    val firstPrice = isPrice.indexOfFirst { it }
    if (firstPrice > 0 && firstPrice == isPrice.size - firstPrice && isPrice.drop(firstPrice).all { it }) {
        return tokens.take(firstPrice).zip(tokens.drop(firstPrice)) { name, price -> "$name $price" }
    }

    val out = mutableListOf<String>()
    val pending = mutableListOf<String>()
    tokens.forEachIndexed { index, token ->
        if (!isPrice[index]) {
            pending.add(token)
        } else if (pending.isEmpty()) {
            if (out.isEmpty()) out.add(token) else out[out.size - 1] = "${out.last()} $token"
        } else {
            out.add("${pending.joinToString(" ")} $token")
            pending.clear()
        }
    }
    if (pending.isNotEmpty()) out.add(pending.joinToString(" "))
    return out.filter { it.isNotBlank() }.ifEmpty { listOf(row) }
}

/** 把「凉拌皮蛋12元」这种名字和价格粘在一起的词拆成两个词，再按空格切。 */
private fun tokenizeRow(row: String): List<String> =
    row.split(' ').filter { it.isNotEmpty() }.flatMap { token ->
        val match = TrailingPrice.matchEntire(token)
        val name = match?.groupValues?.get(1)?.trim().orEmpty()
        val price = match?.groupValues?.get(2)?.trim().orEmpty()
        if (name.isNotEmpty() && HasWord.containsMatchIn(name) && PriceToken.matches(price)) {
            listOf(name, price)
        } else {
            listOf(token)
        }
    }

private fun stripNoiseWords(text: String): String {
    var result = text
    MenuNoiseWords.forEach { word -> result = result.replace(word, " ") }
    return result.replace(Whitespace, " ").trim()
}

/** 这一行现在被认出几个价格（0/1/2），决定还能不能再贴一个价格上来。 */
private fun dishPriceCount(row: String): Int {
    val dish = parseDishLines(row).firstOrNull() ?: return 0
    return (if (dish.price != null) 1 else 0) + (if (dish.groupPrice != null) 1 else 0)
}

/** 按纵向位置把文字行分组：竖直方向重叠得够多就算同一行。 */
private fun groupOcrRows(lines: List<OcrLine>): List<List<OcrLine>> {
    val sorted = lines.sortedWith(compareBy({ it.centerY }, { it.left }))
    val rows = mutableListOf<MutableList<OcrLine>>()
    sorted.forEach { line ->
        val row = rows.lastOrNull()
        if (row != null && sameVisualRow(row, line)) row.add(line) else rows.add(mutableListOf(line))
    }
    return rows
}

private fun sameVisualRow(row: List<OcrLine>, line: OcrLine): Boolean {
    val top = row.minOf { it.top }
    val bottom = row.maxOf { it.bottom }
    val overlap = minOf(bottom, line.bottom) - maxOf(top, line.top)
    val shorter = minOf(bottom - top, line.height)
    return overlap > 0 && overlap * 2 >= shorter
}


/** Keep conditions as evidence before any of the legacy name/price cleanup runs. */
internal fun isPriceCondition(text: String): Boolean = Regex(
    "会员|券后|领券|首单|新客|新人|有效期|使用时间|不可用|节假日|限时|限购|限用|购买须知|使用须知|预约|到期|截止|满\\d|满减|低至|起价|元起|仅限|不可叠加|限堂食|限外带|每桌|每单",
).containsMatchIn(text)

internal fun priceConditionsOf(lines: List<String>): String = lines.filter(::isPriceCondition).distinct().joinToString("\n")

/**
 * Reliable import path. Legacy text packing remains available unchanged for old callers.
 * Two unlabelled numbers are NOT assigned original/discount roles by their magnitude.
 */
fun ocrLinesToDraftDishes(lines: List<OcrLine>): List<DraftDish> {
    val raw = lines.joinToString("\n") { it.text }
    val conditions = priceConditionsOf(lines.map { it.text })
    // Standalone validity/usage lines are evidence, never dishes. Conditional price rows
    // remain available to the packing algorithm so the observed amount is not discarded.
    val conditionBoundary = Regex("有效期|使用时间|不可用日期|购买须知|使用须知|截止日期")
    val menuLines = lines.map { line ->
        val boundary = conditionBoundary.find(line.text)
        val prefix = boundary?.let { line.text.substring(0,it.range.first).trim() }.orEmpty()
        if(prefix.isNotBlank()) line.copy(text=prefix) else line
    }.filterNot { line ->
        isPriceCondition(line.text) &&
            (Regex("有效期|使用时间|不可用|购买须知|使用须知|到期|截止|预约").containsMatchIn(line.text) ||
                !Regex("[¥￥]\\s*\\d|\\d+(?:\\.\\d+)?\\s*元|(?:会员价|券后价|首单价)\\s*\\d").containsMatchIn(line.text))
    }
    return ocrLinesToMenuText(menuLines).lines().filter { it.isNotBlank() }.mapNotNull { packed ->
        val parsed = parseDishLines(packed).singleOrNull() ?: return@mapNotNull null
        // Extract only the trailing amounts. Digits within a dish's name remain part of its name.
        var rest = packed.trim()
        val amounts = mutableListOf<Double>()
        val trailing = Regex("""^(.*?)[\s,，、:：/|]*[¥￥]?(\d+(?:\.\d+)?)\s*(?:元|块)?$""")
        repeat(2) {
            val match = trailing.matchEntire(rest)
            if (match != null && match.groupValues[1].isNotBlank()) {
                match.groupValues[2].toDoubleOrNull()?.let { amounts.add(0, it) }
                rest = match.groupValues[1].trim()
            }
        }
        val invalidAmount = Regex("""(?:^|\s|[¥￥])[-−]\s*\d|NaN|Infinity|∞""", RegexOption.IGNORE_CASE).containsMatchIn(packed) ||
            amounts.any { !it.isFinite() || it < 0.0 }
        val ambiguous = amounts.size > 1
        val evidence = listOf(conditions, if (invalidAmount) "金额无效，请对照原文重新填写" else "", if (ambiguous) "识别到多个价格（原顺序）：${amounts.joinToString("、") { it.yuan() }}；请填写本次适用价格" else "")
            .filter { it.isNotBlank() }.joinToString("\n")
        DraftDish(name = parsed.name, price = if (invalidAmount || ambiguous) null else amounts.firstOrNull(), groupPrice = null,
            rawText = raw, priceConditions = evidence,
            priceConfirmed = !invalidAmount && !ambiguous && conditions.isBlank())
    }
}
