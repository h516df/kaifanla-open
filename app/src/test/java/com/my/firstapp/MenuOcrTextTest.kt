package com.my.firstapp

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 截图识别整理逻辑的测试。
 * 这里的「截图」用 OcrLine 的坐标模拟：left/top/right/bottom 就是识别出的文字框像素位置。
 */
class MenuOcrTextTest {

    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrLine(text, left, top, right, bottom)

    /** 一行文字里既有菜名又有价格时，整理结果应该直接被 parseDishLines 认出来。 */
    private fun dishesOf(text: String) = parseDishLines(text)

    @Test
    fun nameOnLeftPriceOnRightSameRow() {
        // 菜单照片：左边菜名、右边单价，OCR 会给成两个框
        val text = ocrLinesToMenuText(
            listOf(
                line("招牌麻辣烫", 40, 300, 320, 350),
                line("25", 880, 300, 940, 350),
                line("番茄麻辣烫", 40, 380, 320, 430),
                line("25", 880, 380, 940, 430),
            ),
        )
        assertEquals("招牌麻辣烫 25\n番茄麻辣烫 25", text)
        assertEquals(
            listOf(ParsedDish("招牌麻辣烫", 25.0, null), ParsedDish("番茄麻辣烫", 25.0, null)),
            dishesOf(text),
        )
    }

    @Test
    fun nameRowWithOriginalAndGroupPrice() {
        // 团购页表格：菜名 / 门市价 / 团购价 在同一水平线上
        val text = ocrLinesToMenuText(
            listOf(
                line("双人餐", 40, 100, 200, 140),
                line("¥268", 500, 100, 620, 140),
                line("¥198", 800, 100, 920, 140),
            ),
        )
        assertEquals("双人餐 ¥268 ¥198", text)
        // 大的算原价、小的算团购价
        assertEquals(ParsedDish("双人餐", 268.0, 198.0), dishesOf(text).single())
    }

    @Test
    fun stackedNameAndPriceRowsAreMerged() {
        // 团购页常见的竖排：菜名一行，「门市价 ¥268」一行，「抢购价 ¥198」一行
        val text = ocrLinesToMenuText(
            listOf(
                line("双人套餐", 40, 100, 260, 140),
                line("门市价 ¥268", 40, 150, 300, 190),
                line("抢购价 ¥198", 40, 200, 300, 240),
            ),
        )
        assertEquals("双人套餐 ¥268 ¥198", text)
        assertEquals(ParsedDish("双人套餐", 268.0, 198.0), dishesOf(text).single())
    }

    @Test
    fun priceLabelsOnSameRowAreStripped() {
        val text = ocrLinesToMenuText(
            listOf(
                line("双人套餐", 40, 100, 200, 140),
                line("门市价￥268", 300, 100, 520, 140),
                line("团购价￥198", 600, 100, 820, 140),
            ),
        )
        assertEquals("双人套餐 ￥268 ￥198", text)
        assertEquals(ParsedDish("双人套餐", 268.0, 198.0), dishesOf(text).single())
    }

    @Test
    fun salesAndButtonRowsAreDropped() {
        val text = ocrLinesToMenuText(
            listOf(
                line("已售 1000", 40, 100, 200, 140),
                line("月售 500+", 40, 150, 200, 190),
                line("立即抢购", 40, 200, 200, 240),
                line("收藏", 40, 250, 200, 290),
                line("门市价", 40, 300, 200, 340),
                line("评分 4.9", 40, 350, 200, 390),
            ),
        )
        assertEquals("", text)
    }

    @Test
    fun tableHeaderWithoutDishNameIsDropped() {
        // 表头「门市价 团购价」在同一行，去掉标签后什么都不剩
        val text = ocrLinesToMenuText(
            listOf(
                line("门市价", 500, 100, 620, 140),
                line("团购价", 800, 100, 920, 140),
                line("单人套餐", 40, 160, 200, 200),
                line("39", 500, 160, 560, 200),
                line("29.9", 800, 160, 880, 200),
            ),
        )
        assertEquals("单人套餐 39 29.9", text)
        assertEquals(ParsedDish("单人套餐", 39.0, 29.9), dishesOf(text).single())
    }

    @Test
    fun keepsVerticalOrderOfDishes() {
        val text = ocrLinesToMenuText(
            listOf(
                line("酸梅汤", 40, 500, 200, 540),
                line("6", 880, 500, 920, 540),
                line("招牌麻辣烫", 40, 100, 320, 140),
                line("25 19.9", 800, 100, 980, 140),
            ),
        )
        assertEquals("招牌麻辣烫 25 19.9\n酸梅汤 6", text)
        assertEquals(listOf("招牌麻辣烫", "酸梅汤"), dishesOf(text).map { it.name })
    }

    @Test
    fun linesWithoutBoundingBoxesFallBackToRecognizedOrder() {
        // 个别识别结果没有边框，这时不能乱拼行，按识别顺序一行一行保留
        val text = ocrLinesToMenuText(
            listOf(
                OcrLine("招牌麻辣烫 25 19.9"),
                OcrLine("酸梅汤 6"),
            ),
        )
        assertEquals("招牌麻辣烫 25 19.9\n酸梅汤 6", text)
        assertEquals(listOf("招牌麻辣烫", "酸梅汤"), dishesOf(text).map { it.name })
    }

    @Test
    fun blankAndWhitespaceLinesAreIgnored() {
        val text = ocrLinesToMenuText(
            listOf(
                line("   ", 0, 0, 10, 10),
                line("招牌麻辣烫", 40, 100, 320, 140),
                line("25", 880, 100, 940, 140),
            ),
        )
        assertEquals("招牌麻辣烫 25", text)
    }

    @Test
    fun menuTitleRowsBeforeFirstDishAreDropped() {
        // 菜单照片常见排版：顶上「本店菜单」「招牌麻辣烫 杨国福」是标题，不是菜
        val text = ocrLinesToMenuText(
            listOf(
                line("本店菜单", 40, 40, 300, 90),
                line("招牌麻辣烫 杨国福", 40, 120, 460, 170),
                line("招牌麻辣烫", 70, 250, 330, 300),
                line("25", 800, 250, 860, 300),
                line("酸梅汤", 70, 380, 250, 430),
                line("6", 800, 380, 850, 430),
            ),
        )
        assertEquals("招牌麻辣烫 25\n酸梅汤 6", text)
    }

    @Test
    fun dishesWithoutAnyPriceAreKeptWhenNothingHasAPrice() {
        // 一张只拍了菜名的图，不能因为「没有价格」就把整张图判成没认出来
        val text = ocrLinesToMenuText(
            listOf(
                line("招牌麻辣烫", 70, 250, 330, 300),
                line("酸梅汤", 70, 380, 250, 430),
            ),
        )
        assertEquals("招牌麻辣烫\n酸梅汤", text)
    }

    @Test
    fun emptyInputGivesEmptyText() {
        assertEquals("", ocrLinesToMenuText(emptyList()))
    }

    @Test
    fun fullWidthSpacesAndUnitsAreCleaned() {
        val text = ocrLinesToMenuText(
            listOf(
                line("鸡腿饭\u3000大份", 40, 100, 320, 140),
                line("16元", 880, 100, 960, 140),
            ),
        )
        assertEquals("鸡腿饭 大份 16元", text)
        assertEquals(ParsedDish("鸡腿饭 大份", 16.0, null), dishesOf(text).single())
    }

    @Test
    fun appMenuRowWithSeveralColumnsIsSplitIntoDishes() {
        // 真实饿了么/美团菜单页：同一水平线上并排 4 道菜，每道都是「菜名 价格」
        val text = ocrLinesToMenuText(
            listOf(
                line("酸辣猪仔肉", 40, 300, 240, 350),
                line("40元", 300, 300, 380, 350),
                line("香菇炒肉片", 420, 300, 620, 350),
                line("18元", 680, 300, 760, 350),
                line("红烧茄子饭", 800, 300, 1000, 350),
                line("8元", 1060, 300, 1140, 350),
                line("酸菜牛肉饭", 1180, 300, 1380, 350),
                line("15元", 1440, 300, 1520, 350),
            ),
        )
        assertEquals(
            "酸辣猪仔肉 40元\n香菇炒肉片 18元\n红烧茄子饭 8元\n酸菜牛肉饭 15元",
            text,
        )
        assertEquals(listOf("酸辣猪仔肉", "香菇炒肉片", "红烧茄子饭", "酸菜牛肉饭"), dishesOf(text).map { it.name })
    }

    @Test
    fun printedMenuNamesThenPricesArePaired() {
        // 印刷菜单两栏：一串菜名后面跟一串价格，数量相等就一一配上
        val text = ocrLinesToMenuText(
            listOf(
                line("京酱肉丝", 40, 100, 200, 140),
                line("手撕鸭", 220, 100, 340, 140),
                line("40元", 500, 100, 580, 140),
                line("38元", 620, 100, 700, 140),
            ),
        )
        assertEquals("京酱肉丝 40元\n手撕鸭 38元", text)
        assertEquals(listOf(ParsedDish("京酱肉丝", 40.0, null), ParsedDish("手撕鸭", 38.0, null)), dishesOf(text))
    }

    @Test
    fun priceGluedToDishNameIsStillSplit() {
        // OCR 有时不给空格：「凉拌皮蛋12元」也是一个词
        val text = ocrLinesToMenuText(
            listOf(
                line("醋香牛肉", 40, 100, 200, 140),
                line("30元", 300, 100, 380, 140),
                line("凉拌皮蛋12元", 420, 100, 640, 140),
            ),
        )
        assertEquals("醋香牛肉 30元\n凉拌皮蛋 12元", text)
        assertEquals(listOf("醋香牛肉", "凉拌皮蛋"), dishesOf(text).map { it.name })
    }

    @Test
    fun oneDishWithTwoPricesIsNotSplit() {
        // 回归：一道菜「原价 + 团购价」两个价格，不能当成两道菜
        val text = ocrLinesToMenuText(
            listOf(
                line("双人套餐", 40, 100, 200, 140),
                line("¥268", 500, 100, 620, 140),
                line("¥198", 800, 100, 920, 140),
            ),
        )
        assertEquals("双人套餐 ¥268 ¥198", text)
        assertEquals(ParsedDish("双人套餐", 268.0, 198.0), dishesOf(text).single())
    }
}
