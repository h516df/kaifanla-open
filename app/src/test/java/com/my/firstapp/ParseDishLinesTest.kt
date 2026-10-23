package com.my.firstapp

import org.junit.Assert.assertEquals
import org.junit.Test

class ParseDishLinesTest {
    private fun one(line: String) = parseDishLines(line).single()

    @Test
    fun nameWithTwoPrices_biggerIsOriginal() {
        assertEquals(ParsedDish("招牌麻辣烫", 25.0, 19.9), one("招牌麻辣烫 25 19.9"))
        assertEquals(ParsedDish("招牌麻辣烫", 25.0, 19.9), one("招牌麻辣烫 19.9 25"))
    }

    @Test
    fun singlePriceAndNoPrice() {
        assertEquals(ParsedDish("酸梅汤", 6.0, null), one("酸梅汤 6"))
        assertEquals(ParsedDish("酸梅汤", null, null), one("酸梅汤"))
    }

    @Test
    fun digitsInsideNameAreKept() {
        assertEquals(ParsedDish("2号套餐", 30.0, null), one("2号套餐 30"))
        assertEquals(ParsedDish("双人餐3件套", 59.0, 45.0), one("双人餐3件套 ¥59 ¥45"))
    }

    @Test
    fun currencySymbolsUnitsAndSeparators() {
        assertEquals(ParsedDish("可乐", 5.0, null), one("可乐5元"))
        assertEquals(ParsedDish("番茄牛腩饭", 28.0, 22.0), one("番茄牛腩饭，￥28，22块"))
        assertEquals(ParsedDish("鸡腿饭", 16.0, null), one("- 鸡腿饭：16"))
    }

    @Test
    fun samePricesMeansNoGroupPrice() {
        assertEquals(ParsedDish("拌面", 10.0, null), one("拌面 10 10"))
    }

    @Test
    fun blankLinesAndPureNumbersSkipped() {
        val list = parseDishLines("\n招牌麻辣烫 25 19.9\n\n  \n30\n番茄麻辣烫 25\n")
        assertEquals(listOf("招牌麻辣烫", "番茄麻辣烫"), list.map { it.name })
    }

    /**
     * 截图识别整理之后最常见的几种写法（「门市价」「已售」这些噪声词已经在
     * ocrLinesToMenuText 里去掉了）。这些排版来自真实的美团/点评团购页截图。
     */
    @Test
    fun ocrStyleSamples() {
        assertEquals(ParsedDish("双人餐", 268.0, 198.0), one("双人餐 ¥268 ¥198"))
        assertEquals(ParsedDish("单人套餐", 39.0, 29.9), one("单人套餐 39元 29.9元"))
        assertEquals(ParsedDish("3-4人套餐", 198.0, 158.0), one("3-4人套餐 ￥198 ￥158"))
        assertEquals(ParsedDish("招牌牛肉面（大碗）", 28.0, null), one("招牌牛肉面（大碗） 28"))
    }
}
