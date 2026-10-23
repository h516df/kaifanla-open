package com.my.firstapp

import org.junit.Assert.*
import org.junit.Test

class SharedTextImportTest {
    @Test fun sharedConditionalOfferKeepsEvidenceWithoutInventingIdentifiers() {
        val raw = "小店餐厅\n双人套餐 券后价¥29\n有效期至10月31日\nhttps://example.com/offer"
        val scan = scanSharedText(raw)
        assertEquals("小店餐厅", scan.shopName)
        assertEquals(raw, scan.rawText)
        val deal = scan.deals.single()
        assertEquals(29.0, deal.currentPrice!!, 0.001)
        assertTrue(deal.conditionsText.contains("券后"))
        assertTrue(deal.conditionsText.contains("有效期"))
        assertEquals(raw, deal.rawText)
        assertEquals("", deal.sourceItemId)
        assertEquals("", deal.platform)
        assertTrue(scan.dishes.isEmpty())
        assertFalse(mergeScans(listOf(scan), "text").deals.single().priceConfirmed)
    }
    @Test fun multipleSharedAmountsStayUnknownEvenAfterConfirmation() {
        val scan = scanSharedText("牛肉面 19 29")
        val dish = scan.pricedDishes.single()
        assertNull(dish.price)
        assertNull(dish.groupPrice)
        assertTrue(dish.priceConditions.contains("¥19、¥29"))
        val draft = mergeScans(listOf(scan), "text").copy(shopName = "店",
            dishes = listOf(dish.copy(priceConfirmed = true)))
        assertNull(applyDraft(emptyList(), draft, { 1L }, 1L).single().dishes.single().paid)
    }
    @Test fun singlePlainMenuPriceSurvivesAndPackageDoesNotBecomeADish() {
        assertEquals(19.0, scanSharedText("牛肉面 19").pricedDishes.single().price!!, 0.001)
        val offer = scanSharedText("双人套餐 29 59")
        assertEquals(1, offer.deals.size)
        assertTrue(offer.dishes.isEmpty())
        assertNull(offer.deals.single().currentPrice)
    }
    @Test fun inlineExpiryDoesNotDiscardOfferOrBecomeItsPrice() {
        val scan = scanSharedText("双人套餐 会员价¥29 有效期至10月31日")
        assertEquals(29.0, scan.deals.single().currentPrice!!, 0.001)
        assertTrue(scan.deals.single().conditionsText.contains("有效期"))
    }
}
