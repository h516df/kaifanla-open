package com.my.firstapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DataJsonTest {
    private val data = AppData(
        shops = listOf(Shop(1, "麦当劳", "🍔", listOf(Dish(2, "巨无霸套餐", 39.0, 29.9), Dish(3, "薯条", null, null)))),
        history = listOf(Record(100, "🍔", "麦当劳", "巨无霸套餐", 39.0, 29.9)),
        settings = Settings(
            nickname = "宝宝", theme = "mint", sound = false, budget = 30.0, avoidDays = 3,
            lastAutoBackupDay = "2026-10-02",
        ),
        skip = SkipToday("2026-10-02", setOf(1), setOf(3)),
    )

    @Test
    fun roundTrip() {
        assertEquals(data, DataJson.decode(DataJson.encode(data)))
    }

    @Test
    fun sharedTextWithReadableMenuInFront() {
        val shared = "【今天吃什么 · 我的店铺菜单】\n\n🍔 麦当劳\n  · 巨无霸套餐 团购¥29.9（原价¥39）\n\n长按复制～\n" +
            DataJson.encode(data) + "\n"
        assertEquals(data.shops, DataJson.decode(DataJson.extract(shared)).shops)
    }

    @Test
    fun oldV1FileWithoutSettingsStillLoads() {
        val v1 = """{"shops":[{"id":1,"name":"沙县小吃","emoji":"🥟","dishes":[{"id":2,"name":"蒸饺","price":8}]}],"history":[]}"""
        val d = DataJson.decode(v1)
        assertEquals("沙县小吃", d.shops.single().name)
        assertEquals(Settings(), d.settings)
        assertEquals("", d.shops.single().photo)
    }

    @Test
    fun shopPhotoFilenameRoundTrips() {
        val withPhoto = data.copy(shops = data.shops.map { it.copy(photo = "123e4567-e89b-12d3-a456-426614174000.jpg") })
        assertEquals(withPhoto, DataJson.decode(DataJson.encode(withPhoto)))
    }

    @Test
    fun oldSettingsWithoutAutoBackupDayStillLoads() {
        val old = """{"shops":[],"history":[],"settings":{"nickname":"旧数据"}}"""
        assertEquals("", DataJson.decode(old).settings.lastAutoBackupDay)
    }

    @Test
    fun dealRoundTrips() {
        val withDeals = data.copy(
            shops = data.shops.map { shop ->
                shop.copy(
                    deals = listOf(
                        Deal(
                            id = 9,
                            title = "【一元】福利购香锅面一块…",
                            currentPrice = 1.0,
                            originalPrice = 2.0,
                            soldText = "已售3万+",
                            tag = "5折",
                            source = "doushengsheng",
                            sourceUrl = "https://www.douyin.com/share/abc",
                            lastSeenAt = 1791000000000L,
                        ),
                        Deal(id = 10, title = "只有名字的团购"),
                    ),
                )
            },
        )
        assertEquals(withDeals, DataJson.decode(DataJson.encode(withDeals)))
    }

    @Test
    fun oldFileWithoutDealsLoadsAsEmptyList() {
        // v2.2 及更早的 data.json 没有 deals 字段，读进来必须是空列表而不是崩
        val v22 = """{"shops":[{"id":1,"name":"沙县小吃","emoji":"🥟","dishes":[{"id":2,"name":"蒸饺","price":8}]}],"history":[]}"""
        assertEquals(emptyList<Deal>(), DataJson.decode(v22).shops.single().deals)
    }

    @Test
    fun missingOptionalCollectionsStayCompatibleButWrongTypesFail() {
        val old = """{"shops":[{"id":1,"name":"旧店"}],"history":[],"skip":{}}"""
        assertEquals(emptyList<Dish>(), DataJson.decode(old).shops.single().dishes)

        listOf(
            """{"shops":[{"id":1,"name":"旧店","dishes":{}}],"history":[]}""",
            """{"shops":[],"history":[],"settings":[]}""",
            """{"shops":[],"history":[],"skip":{"shopIds":"1"}}""",
        ).forEach { bad ->
            assertThrows(Exception::class.java) { DataJson.decode(bad) }
        }
    }
}

