package com.my.firstapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 抖省省结构化识别的测试。
 *
 * 坐标是照着真实手机截图的比例编的（1080 宽、内容从上往下排），
 * 页面内容用的是 Issue #35 里用户给的真实字段：
 * 示例川味小馆(示例店) / 示例市示例大道 1 号示例街 c-252 / 5 条团购。
 */
class DoushengshengScanTest {

    private var top = 0

    /** 往页面上追加一行，gap 是这一行和上一行之间的空隙。 */
    private fun line(text: String, gap: Int = 12, height: Int = 30, left: Int = 60, right: Int = 900): OcrLine {
        val t = top + gap
        val box = OcrLine(text, left, t, right, t + height)
        top = t + height
        return box
    }

    private fun reset(start: Int = 100) {
        top = start
    }

    private fun shopPageLines(): List<OcrLine> {
        reset(120)
        return listOf(
            line("示例川味小馆(示例店)", gap = 0, height = 70),
            line("208米", gap = 16, height = 26),
            line("4.8分 人均¥30", gap = 8, height = 26),
            line("示例市示例大道 1 号示例街 c-252", gap = 8, height = 30),
        )
    }

    /** 一张团购卡片：标题 + 现价（框更高）+ 划线原价 + 销量 + 折扣标签。 */
    private fun dealCard(title: String, current: String, original: String, sold: String, tag: String, gapBefore: Int): List<OcrLine> =
        listOf(
            line(title, gap = gapBefore, height = 32, right = 1000),
            line(current, gap = 10, height = 48, right = 260),
            line(original, gap = 6, height = 26, right = 200),
            line(sold, gap = 6, height = 24, right = 300),
            line(tag, gap = 6, height = 24, right = 240),
        )

    private fun dealListPageLines(): List<OcrLine> {
        reset(140)
        return buildList {
            add(line("示例川味小馆(示例店)", gap = 0, height = 60))
            add(line("优惠团购", gap = 20, height = 28))
            addAll(dealCard("【示例·自选菜品】堂食40元自选…", "¥36.6", "¥40.04", "已售9000+", "5.8折", 24))
            addAll(dealCard("【示例·菜品20元自选】…", "¥18.6", "¥20.02", "已售2000+", "9.3折", 60))
            addAll(dealCard("【招牌必点】超值单人餐(6荤6素)…", "¥17.8", "¥31.01", "已售1万+", "5.7折", 60))
            addAll(dealCard("【示例小馆·菜品60元自选】…", "¥56.6", "¥60.06", "已售500+", "9.4折", 60))
            addAll(dealCard("【一元】福利购香锅面一块…", "¥1", "¥2", "已售3万+", "5折", 60))
        }
    }

    @Test
    fun shopPageExtractsNameAndAddress() {
        val scan = scanDoushengsheng(shopPageLines())
        assertEquals("示例川味小馆(示例店)", scan.shopName)
        assertEquals("示例市示例大道 1 号示例街 c-252", scan.address)
        // 距离是动态信息，不能当地址也不能当价格
        assertFalse(scan.address.contains("208"))
    }

    @Test
    fun dealListExtractsFiveDealsWithBothPrices() {
        val scan = scanDoushengsheng(dealListPageLines())
        assertEquals(5, scan.deals.size)

        val first = scan.deals.first()
        assertEquals("【示例·自选菜品】堂食40元自选…", first.title)
        assertEquals(36.6, first.currentPrice!!, 0.001)
        assertEquals(40.04, first.originalPrice!!, 0.001)
        assertEquals("已售9000+", first.soldText)
        assertEquals("5.8折", first.tag)

        assertEquals(listOf(36.6, 18.6, 17.8, 56.6, 1.0), scan.deals.map { it.currentPrice })
        assertEquals(listOf(40.04, 20.02, 31.01, 60.06, 2.0), scan.deals.map { it.originalPrice })
    }

    @Test
    fun soldTextAndDiscountNeverBecomePrice() {
        val scan = scanDoushengsheng(dealListPageLines())
        val all = scan.deals.flatMap { listOfNotNull(it.currentPrice, it.originalPrice) }
        assertFalse("销量不能被当成价格", all.contains(9000.0))
        assertFalse("折扣不能被当成价格", all.contains(5.8))
        assertFalse("距离不能被当成价格", all.contains(208.0))
    }

    @Test
    fun distanceAndDiscountAreNotPrices() {
        assertNull(priceValueOf("208米"))
        assertNull(priceValueOf("5.8折"))
        assertNull(priceValueOf("已售9000+"))
        assertEquals(36.6, priceValueOf("¥36.6")!!, 0.001)
        assertEquals(1.0, priceValueOf("¥1")!!, 0.001)
    }

    @Test
    fun gluedPriceAndDiscountLineIsSplitCorrectly() {
        // 真实抖省省 OCR 会把「现价 ￥5.9 / 原价 ￥7 / 8.5折」粘成一行，折扣帮着把 7 和 78.5 分开
        val m = Regex("""[¥￥]\s*(\d{1,6}(?:\.\d{1,2})?)\s*[¥￥]\s*(\d{1,6}(?:\.\d{1,2})?)\s*(\d(?:\.\d{1,2})?)\s*折""")
            .find("￥5.9￥78.5折热销中")!!
        assertEquals("5.9", m.groupValues[1])
        assertEquals("7", m.groupValues[2])
        assertEquals("8.5", m.groupValues[3])
    }

    @Test
    fun realDealCardWithGluedPricesIsParsed() {
        reset(140)
        val lines = buildList {
            add(line("岷阳火锅鸡", gap = 0, height = 60))
            add(line("【火锅鸡】小锅", gap = 40, height = 34))
            add(line("￥210￥2388.9折", gap = 12, height = 40))
            add(line("已售43", gap = 10, height = 26))
            add(line("【火锅鸡】中锅", gap = 60, height = 34))
            add(line("￥280", gap = 12, height = 40))
            add(line("￥3089.1折", gap = 8, height = 30))
        }
        val scan = scanDoushengsheng(lines)
        assertEquals("岷阳火锅鸡", scan.shopName)
        assertEquals(2, scan.deals.size)
        assertEquals("【火锅鸡】小锅", scan.deals[0].title)
        assertEquals(210.0, scan.deals[0].currentPrice!!, 0.001)
        assertEquals(238.0, scan.deals[0].originalPrice!!, 0.001)
        assertEquals("8.9折", scan.deals[0].tag)
        assertEquals(280.0, scan.deals[1].currentPrice!!, 0.001)
        assertEquals(308.0, scan.deals[1].originalPrice!!, 0.001)
        assertEquals("9.1折", scan.deals[1].tag)
        assertEquals("已售43", scan.deals[0].soldText)
    }

    @Test
    fun promoPriceLineWithoutDiscountIsStillAPrice() {
        // 真实抖省省：￥0.01首单抄底价
        assertEquals(listOf(0.01), pricesInLine("￥0.01首单抄底价"))
        assertEquals(listOf(5.8), pricesInLine("￥5.8省10元"))
        // 带数字的标题不是价格行
        assertEquals(emptyList<Double>(), pricesInLine("【示例·菜品20元自选】"))
        assertEquals(emptyList<Double>(), pricesInLine("4.638条评价"))
        assertEquals(emptyList<Double>(), pricesInLine("0.7km"))
    }

    @Test
    fun statusBarTimeAndBatteryAreNotPrices() {
        // 真实截图顶上的状态栏：时间 / 电量百分比 / 信号，整行丢掉
        assertEquals(emptyList<Double>(), pricesInLine("17:31"))
        assertEquals(emptyList<Double>(), pricesInLine("44%"))
        assertEquals(emptyList<Double>(), pricesInLine("5G"))
        // 光秃秃的数字在菜单里仍可能是价格，交给「是不是在页面最顶上」那层去判断
        assertEquals(listOf(42.0), pricesInLine("42"))
    }

    @Test
    fun statusBarBatteryCardDoesNotBecomeADeal() {
        // 真实 ML Kit 输出里状态栏被读成「::!!924」，它不能变成一条 ¥924 的团购
        reset(26)
        val lines = listOf(
            OcrLine("::!!924", 395, 26, 498, 45),
            OcrLine("17:31", 61, 27, 114, 44),
            OcrLine("初予食禾智慧餐厅", 23, 281, 288, 312),
            OcrLine("【新窖特惠肉包+豆浆+茶叶蛋一单人营养套", 151, 676, 516, 695),
            OcrLine("已售43", 466, 710, 516, 724),
            OcrLine("|¥5.9¥7l8.5折热销中", 143, 768, 306, 785),
        )
        val scan = scanDoushengsheng(lines)
        assertEquals(1, scan.deals.size)
        assertEquals(5.9, scan.deals[0].currentPrice!!, 0.001)
    }

    @Test
    fun mlKitNoiseInPriceLineStillParses() {
        // 真实 ML Kit 输出：¥ 被认成 Y，¥7 后面粘了个 l
        assertEquals(listOf(5.9, 7.0), pricesInLine("|¥5.9¥7l8.5折热销中"))
        assertEquals(listOf(9.9, 14.0), pricesInLine("Y9.9 ¥14"))
        // 有中文的行仍然不是价格行
        assertEquals(emptyList<Double>(), pricesInLine("到店使用周一至周日可用 免预约"))
        assertEquals(emptyList<Double>(), pricesInLine("含「茶叶蛋」等2个推荐菜"))
    }

    @Test
    fun realMlKitShopPageProducesTwoDeals() {
        // 下面这些行是真实截图 dss-shop-A 经 ML Kit 识别后的原文（坐标按原比例缩小）
        val raw = listOf(
            Triple("17:31", 61, 27),
            Triple("初予食禾智慧餐厅", 23, 281),
            Triple("白云区科园路2号铂中心1栋2层206~208", 24, 458),
            Triple("室1距你88米,步行2分钟", 23, 484),
            Triple("优惠团购", 24, 630),
            Triple("【新窖特惠肉包+豆浆+茶叶蛋一单人营养套", 151, 676),
            Triple("到店使用周一至周日可用 免预约", 156, 710),
            Triple("已售43", 466, 710),
            Triple("含「茶叶蛋」等2个推荐菜", 150, 737),
            Triple("|¥5.9¥7l8.5折热销中", 143, 768),
            Triple("【匠心现制】单人杂酱面1份+卤蛋1个", 151, 815),
            Triple("|到店使用周一至周日可用 免预约", 149, 847),
            Triple("已售13", 462, 848),
            Triple("Y9.9 ¥14", 151, 885),
            Triple("07.1折热銷中", 153, 909),
        )
        val lines = raw.map { (text, left, top) -> OcrLine(text, left, top, left + 200, top + 18) }
        val scan = scanForImport(lines)
        assertEquals("初予食禾智慧餐厅", scan.shopName)
        assertTrue(scan.address.contains("白云区科园路2号"))
        assertEquals(2, scan.deals.size)
        assertEquals("【新窖特惠肉包+豆浆+茶叶蛋一单人营养套", scan.deals[0].title)
        assertEquals(5.9, scan.deals[0].currentPrice!!, 0.001)
        assertEquals(7.0, scan.deals[0].originalPrice!!, 0.001)
        assertEquals("8.5折", scan.deals[0].tag)
        assertEquals("已售43", scan.deals[0].soldText)
        assertEquals(9.9, scan.deals[1].currentPrice!!, 0.001)
        assertEquals(14.0, scan.deals[1].originalPrice!!, 0.001)
        assertEquals("7.1折", scan.deals[1].tag)
    }

    @Test
    fun shopNamePrefersNameLikeLineOverSlogan() {
        // 真实店铺页顶上先出现宣传语「肉香与菜鲜共舞」，字号还更大；店名要挑带「餐厅」的那行
        reset(60)
        val lines = buildList {
            add(line("17:31", gap = 0, height = 22, left = 30, right = 130))
            add(line("924", gap = 0, height = 22, left = 200, right = 260))
            add(line("肉香与菜鲜共舞", gap = 30, height = 70))
            add(line("初予食禾智慧餐厅", gap = 10, height = 54))
            add(line("白云区科园路2号凯铂中心1栋2层206~208", gap = 20, height = 30))
            add(line("￥30/人|快餐", gap = 30, height = 28))
            add(line("优惠团购", gap = 30, height = 30))
            add(line("【新客特惠】肉包+豆浆+茶叶蛋|单人营养套", gap = 40, height = 34))
            add(line("已售43", gap = 10, height = 26))
            add(line("￥5.9￥78.5折热销中", gap = 8, height = 42))
        }
        val scan = scanDoushengsheng(lines)
        assertEquals("初予食禾智慧餐厅", scan.shopName)
        assertEquals("白云区科园路2号凯铂中心1栋2层206~208", scan.address)
        assertEquals(1, scan.deals.size)
        assertEquals(5.9, scan.deals[0].currentPrice!!, 0.001)
        assertEquals(7.0, scan.deals[0].originalPrice!!, 0.001)
    }

    @Test
    fun realShopPageWithFullAddressIsParsed() {
        // 真实店铺详情页（公开来源截图 dss-shop-A）：店名 + 完整地址 + 3 条团购
        reset(120)
        val lines = listOf(
            line("初予食禾智慧餐厅", gap = 0, height = 64),
            line("￥30/人|快餐", gap = 16, height = 28),
            line("白云区科园路2号凯铂中心1栋2层206~208", gap = 10, height = 30),
            line("室1距你88米，步行2分钟", gap = 6, height = 28),
            line("优惠团购", gap = 40, height = 30),
            line("【新客特惠】肉包+豆浆+茶叶蛋|单人营养套", gap = 30, height = 34),
            line("已售43", gap = 10, height = 26),
            line("￥5.9￥78.5折热销中", gap = 8, height = 42),
            line("【匠心现制】单人杂酱面1份+卤蛋1个", gap = 60, height = 34),
            line("已售13", gap = 10, height = 26),
            line("￥9.9￥147.1折热销中", gap = 8, height = 42),
        )
        val scan = scanDoushengsheng(lines)
        assertEquals("初予食禾智慧餐厅", scan.shopName)
        assertTrue("地址要认出区/路/号", scan.address.contains("白云区科园路2号"))
        assertEquals(2, scan.deals.size)
        assertEquals(5.9, scan.deals[0].currentPrice!!, 0.001)
        assertEquals(7.0, scan.deals[0].originalPrice!!, 0.001)
        assertEquals(9.9, scan.deals[1].currentPrice!!, 0.001)
        assertEquals(14.0, scan.deals[1].originalPrice!!, 0.001)
    }

    @Test
    fun recommendedDishesBecomeDishCandidates() {
        reset(140)
        val lines = buildList {
            add(line("示例川味小馆(示例店)", gap = 0, height = 60))
            addAll(dealCard("【一元】福利购香锅面一块…", "¥1", "¥2", "已售3万+", "5折", 40))
            add(line("用户推荐菜", gap = 80, height = 30))
            add(line("招牌麻辣香锅 128人推荐", gap = 24, height = 28))
            add(line("麻辣香锅", gap = 24, height = 28))
            add(line("宽粉", gap = 24, height = 28))
        }
        val scan = scanDoushengsheng(lines)
        assertEquals(listOf("招牌麻辣香锅", "麻辣香锅", "宽粉"), scan.dishes)
        // 团购和推荐菜必须分开：代金券不能混进菜品
        assertTrue(scan.deals.none { it.title in scan.dishes })
    }

    @Test
    fun multipleScreenshotsMergeIntoOneDraft() {
        val shopPage = scanDoushengsheng(shopPageLines())
        val dealPage = scanDoushengsheng(dealListPageLines())
        val draft = mergeScans(listOf(shopPage, dealPage), source = "doushengsheng")

        // 第二张没有完整店名，店名和地址来自第一张
        assertEquals("示例川味小馆(示例店)", draft.shopName)
        assertEquals("示例市示例大道 1 号示例街 c-252", draft.address)
        assertEquals(5, draft.deals.size)
    }

    @Test
    fun duplicateTitlesKeepConflictingScreenshotEvidence() {
        val first = scanDoushengsheng(dealListPageLines())
        reset(140)
        val second = scanDoushengsheng(
            buildList {
                add(line("示例川味小馆(示例店)", gap = 0, height = 60))
                addAll(dealCard("【一元】福利购香锅面一块…", "¥1.5", "¥2", "已售3万+", "7.5折", 40))
            },
        )
        val draft = mergeScans(listOf(first, second), source = "doushengsheng")
        // Deliberate policy change: the later screenshot is not proof that the old price expired.
        assertEquals(6, draft.deals.size)
        val offers = draft.deals.filter { it.title.startsWith("【一元】") }
        assertEquals(listOf(1.0, 1.5), offers.map { it.currentPrice })
    }

    @Test
    fun applyingDraftMergesIntoExistingShopInsteadOfCreatingNewOne() {
        val existing = listOf(
            Shop(
                id = 1,
                name = "示例川味小馆(示例店)",
                emoji = "🍲",
                dishes = listOf(Dish(11, "宽粉", 6.0, null)),
                address = "",
                deals = listOf(Deal(id = 21, title = "【一元】福利购香锅面一块…", currentPrice = 1.0, originalPrice = 2.0)),
            ),
        )
        val draft = attachToShops(existing, mergeScans(listOf(scanDoushengsheng(shopPageLines()), scanDoushengsheng(dealListPageLines())), "doushengsheng"))
        var next = 100L
        val result = applyDraft(existing, draft, newId = { next++ }, now = 1234L)

        assertEquals("同店不能重复新建", 1, result.size)
        val shop = result.single()
        assertEquals("示例市示例大道 1 号示例街 c-252", shop.address)
        assertEquals("同名不再授权覆盖旧报价", 6, shop.deals.size)
        val one = shop.deals.first { it.title.startsWith("【一元】") }
        assertEquals(1.0, one.currentPrice!!, 0.001)
        assertEquals("同名菜不重复添加", 1, shop.dishes.count { it.name == "宽粉" })
        assertEquals(0L, one.lastSeenAt)
        val imported = shop.deals.last { it.title.startsWith("【一元】") }
        assertEquals(1234L, imported.importedAt)
        assertEquals(null, imported.observedAt)
    }

    @Test
    fun newDealIsAddedAndOldDealIsKeptWhenNotSeen() {
        val existing = listOf(
            Shop(
                id = 1,
                name = "示例川味小馆(示例店)",
                emoji = "🍲",
                dishes = emptyList(),
                deals = listOf(Deal(id = 21, title = "【老团购】已经下架的套餐", currentPrice = 9.9)),
            ),
        )
        reset(140)
        val page = scanDoushengsheng(
            buildList {
                add(line("示例川味小馆(示例店)", gap = 0, height = 60))
                addAll(dealCard("【新团购】双人餐…", "¥28.8", "¥45", "已售10+", "6.4折", 40))
            },
        )
        val draft = attachToShops(existing, mergeScans(listOf(page), "doushengsheng"))
        assertEquals(listOf("【老团购】已经下架的套餐"), draft.missingDeals)

        val result = applyDraft(existing, draft, newId = { 200L }, now = 1L)
        val shop = result.single()
        assertEquals("本次没出现的旧团购不删", 2, shop.deals.size)
        assertTrue(shop.deals.any { it.title.startsWith("【老团购】") })
        assertTrue(shop.deals.any { it.title.startsWith("【新团购】") })
    }

    @Test
    fun brandNewShopIsCreatedWithDealsAndDishes() {
        val draft = mergeScans(listOf(scanDoushengsheng(dealListPageLines())), "doushengsheng")
        var next = 1L
        val result = applyDraft(emptyList(), draft, newId = { next++ }, now = 5L)
        val shop = result.single()
        assertEquals("示例川味小馆(示例店)", shop.name)
        assertEquals(5, shop.deals.size)
        assertEquals(0, shop.dishes.size)
        assertEquals("doushengsheng", shop.deals.first().source)
        assertTrue("截图导入不能伪造商品链接", shop.deals.all { it.sourceUrl.isBlank() })
    }

    @Test
    fun uncheckedItemsAreNotImported() {
        val draft = mergeScans(listOf(scanDoushengsheng(dealListPageLines())), "doushengsheng")
        val trimmed = draft.copy(
            deals = draft.deals.mapIndexed { i, d -> if (i == 0) d.copy(checked = false) else d },
            dishes = listOf(DraftDish("宽粉", checked = false)),
        )
        val result = applyDraft(emptyList(), trimmed, newId = { 1L }, now = 1L)
        assertEquals(4, result.single().deals.size)
        assertEquals(0, result.single().dishes.size)
    }

    @Test
    fun sharedTextKeepsUrlButDoesNotInventOne() {
        val withUrl = parseSharedText("示例小馆麻辣香锅 4.8分 https://www.douyin.com/share/abc123 快来看看")
        assertEquals("https://www.douyin.com/share/abc123", withUrl.url)
        val withoutUrl = parseSharedText("示例川味小馆(示例店) 团购超划算")
        assertEquals("", withoutUrl.url)
    }

    @Test
    fun plainMenuPhotoFallsBackToOldLinePacking() {
        // 普通菜单照片没有团购区块，应该退回 #7 的「按行拼菜名+价格」，老能力不丢
        reset(100)
        val lines = listOf(
            line("招牌麻辣烫", gap = 0, height = 30, right = 400),
            line("25", gap = 0, height = 30, left = 800, right = 880),
            line("酸梅汤", gap = 60, height = 30, right = 300),
            line("6", gap = 0, height = 30, left = 800, right = 860),
        )
        val scan = scanForImport(lines)
        assertEquals(listOf("招牌麻辣烫", "酸梅汤"), scan.dishes)
        assertEquals(listOf(25.0, 6.0), scan.pricedDishes.map { it.price })
    }

    @Test fun malformedAmountsAreNotTruncatedIntoPrices() {
        listOf("Y6e-06", "¥40-04", "¥18.620-02", "¥1234567", "94折").forEach {
            assertTrue("Unexpected price from $it: ${pricesInLine(it)}", pricesInLine(it).isEmpty())
        }
        assertEquals(listOf(56.6), pricesInLine("56.6 6e-06"))
        assertEquals(listOf(18.6, 20.02), pricesInLine("¥18.6 ¥20.02"))
    }

    @Test fun cardWithUnreadableAmountRemainsAvailableForCorrection() {
        reset()
        val scan = scanDoushengsheng(listOf(
            line("团购套餐"), line("【示例小馆菜品20元自选】"),
            line("¥当8.620-02"), line("已售2000+"), line("9.3折")))
        assertEquals(1, scan.deals.size)
        assertEquals("【示例小馆菜品20元自选】", scan.deals.single().title)
        assertNull(scan.deals.single().currentPrice)
        assertNull(scan.deals.single().originalPrice)
        assertTrue(scan.notes.any { it.contains("金额") })
    }

    @Test fun corruptedSecondAmountNeverBecomesLowerCurrentPrice() {
        reset()
        val scan = scanDoushengsheng(listOf(
            line("团购套餐"), line("【示例小馆菜品60元自选】"),
            line("56.6 6e-06"), line("已售500+"), line("94折")))
        assertEquals(1, scan.deals.size)
        assertEquals(56.6, scan.deals.single().currentPrice!!, 0.001)
        assertNull(scan.deals.single().originalPrice)
        assertTrue(scan.notes.any { it.contains("金额") })
    }
}

