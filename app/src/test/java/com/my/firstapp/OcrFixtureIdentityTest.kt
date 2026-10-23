package com.my.firstapp

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

/** Real ML Kit output from two synthetic screenshots; not user/private images. */
class OcrFixtureIdentityTest {
    private fun scans(): List<PageScan> {
        val raw = javaClass.getResourceAsStream("/ocr-fixture-lines.json")!!.bufferedReader().use { it.readText() }
        val pages = JSONArray(raw)
        return (0 until pages.length()).map { page ->
            val lines = pages.getJSONObject(page).getJSONArray("lines")
            scanDoushengsheng((0 until lines.length()).map { i ->
                val line = lines.getJSONObject(i)
                OcrLine(line.getString("text"), line.getInt("left"), line.getInt("top"), line.getInt("right"), line.getInt("bottom"))
            })
        }
    }
    @Test fun recommendationCountsFromActualOcrAreNotDishCandidates() {
        org.junit.Assert.assertFalse(scans().flatMap { it.dishes }.contains("34人推"))
        assertEquals(listOf("土豆片", "宽粉", "招牌麻辣香锅", "牛肉丸").sorted(), scans().flatMap { it.dishes }.sorted())
    }
    @Test fun shortenedCountsAreRemovedWithoutDroppingNamedDishes() {
        val scan = scanDoushengsheng(listOf(
            OcrLine("用户推荐菜", 0, 0, 300, 30),
            OcrLine("34人推", 0, 45, 300, 75),
            OcrLine("土豆片 34人推", 0, 90, 300, 120),
            OcrLine("招牌麻辣香锅 20人推荐", 0, 135, 400, 165),
            OcrLine("人气炒饭", 0, 180, 300, 210),
        ))
        assertEquals(listOf("土豆片", "招牌麻辣香锅", "人气炒饭"), scan.dishes)
    }
    @Test fun dealOnlyPageDoesNotInventAShopNameFromThePrice() {
        assertEquals("", scans()[1].shopName)
    }
    @Test fun galleryShareOrderDoesNotReplaceTheRealShopName() {
        val scans = scans()
        assertEquals("示例川味小馆(示例店)", mergeScans(scans, "doushengsheng").shopName)
        assertEquals("示例川味小馆(示例店)", mergeScans(scans.reversed(), "doushengsheng").shopName)
    }
    @Test fun corruptedOriginalAndDiscountDoNotReplaceTheActualFixtureCurrentPrice() {
        val deal = scans()[1].deals.single { it.title.contains("60元") }
        assertEquals(56.6, deal.currentPrice!!, 0.001)
        org.junit.Assert.assertNull(deal.originalPrice)
        assertEquals("", deal.tag)
        assertEquals(emptyList<Double>(), pricesInLine("94折"))
        assertEquals("", discountInLine("94折"))
    }}

