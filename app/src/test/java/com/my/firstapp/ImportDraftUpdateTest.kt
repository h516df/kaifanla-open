package com.my.firstapp

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportDraftUpdateTest {
    @Test
    fun menuOnlyImportUsesTheSelectedShopsName() {
        val shop = Shop(2, "店铺乙", "🍲", emptyList())
        val draft = ImportDraft(matchedShopId = 2, dishes = listOf(DraftDish("炒饭", 12.0)))
        val attached = attachToShops(listOf(shop), draft)
        assertEquals("店铺乙", attached.shopName)
        assertEquals(2L, attached.matchedShopId)
    }

    @Test
    fun editedExistingDealTitleIsSavedWithoutAddingAnotherDeal() {
        val shop = Shop(1, "店铺", "🍲", emptyList(), deals = listOf(Deal(21, "识别错的标题", 10.0, platform = "meituan", sourceItemId = "real-1")))
        val draft = ImportDraft(shopName = "店铺", matchedShopId = 1,
            deals = listOf(DraftDeal("修正后的标题", currentPrice = 12.0, existingId = 21, platform = "meituan", sourceItemId = "real-1", resolution = DealResolution.REPLACE, expectedExisting = shop.deals.single())))
        val result = applyDraft(listOf(shop), draft, { 100L }, 123L).single()
        assertEquals(1, result.deals.size)
        assertEquals(21L, result.deals.single().id)
        assertEquals("修正后的标题", result.deals.single().title)
        assertEquals(12.0, result.deals.single().currentPrice!!, 0.001)
    }

    @Test
    fun selectedShopIdWinsOverAnotherShopsMatchingName() {
        val first = Shop(1, "店铺甲", "🍲", emptyList())
        val selected = Shop(2, "店铺乙", "🍲", emptyList())
        val draft = ImportDraft(shopName = "店铺甲", matchedShopId = 2,
            deals = listOf(DraftDeal("套餐", currentPrice = 12.0)))
        val result = applyDraft(listOf(first, selected), draft, { 100L }, 123L)
        assertEquals(first, result.first())
        assertEquals(1, result.last().deals.size)
        assertEquals(2L, attachToShops(listOf(first, selected), draft).matchedShopId)
    }

    @Test
    fun selectedDealIdWinsOverAnotherDealsMatchingTitle() {
        val first = Deal(21, "套餐甲", 10.0)
        val selected = Deal(22, "套餐乙", 20.0, platform = "meituan", sourceItemId = "real-2")
        val shop = Shop(1, "店铺", "🍲", emptyList(), deals = listOf(first, selected))
        val draft = ImportDraft(shopName = "店铺", matchedShopId = 1,
            deals = listOf(DraftDeal("套餐甲", currentPrice = 12.0, existingId = 22, platform = "meituan", sourceItemId = "real-2", resolution = DealResolution.REPLACE, expectedExisting = selected)))
        val deals = applyDraft(listOf(shop), draft, { 100L }, 123L).single().deals
        assertEquals(first, deals.first())
        assertEquals(12.0, deals.last().currentPrice!!, 0.001)
    }
}

