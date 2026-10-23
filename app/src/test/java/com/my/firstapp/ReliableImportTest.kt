package com.my.firstapp

import org.junit.Assert.*
import org.junit.Test

class ReliableImportTest {
    private val old = Deal(10, "双人餐", 39.0, platform = "meituan", sourceItemId = "sku-1", rawText = "旧证据")
    private val shop = Shop(1, "小店", "🍜", emptyList(), deals = listOf(old))
    private fun incoming() = DraftDeal("双人餐", 29.0, platform = "meituan", sourceItemId = "sku-1",
        rawText = "会员券后 ¥29，有效期10月", conditionsText = "会员券后；有效期10月")
    private fun draft(d: DraftDeal) = ImportDraft(shopName = "小店", deals = listOf(d), source = "photo")
    private fun apply(d: DraftDeal, shops: List<Shop> = listOf(shop), retainRawText: Boolean = false) =
        applyDraft(shops, draft(d).copy(retainRawText = retainRawText), { 99L }, 200L).single()

    @Test fun sameSkuOnlySuggestsAndDefaultsToAdding() {
        val attached = attachToShops(listOf(shop), draft(incoming()))
        val d = attached.deals.single()
        assertEquals(10L, d.suspectedExistingId)
        assertEquals(old, d.expectedExisting)
        assertNull(d.existingId)
        assertEquals(DealResolution.ADD, d.resolution)
        assertEquals(listOf(old, apply(d).deals.last()), apply(d).deals)
        assertEquals(2, apply(d).deals.size)
    }
    @Test fun crossPlatformCannotReplaceEvenWithMatchingSkuAndExplicitId() {
        val d = incoming().copy(platform = "eleme", resolution = DealResolution.REPLACE, existingId = 10, expectedExisting = old)
        assertThrows(ImportValidationException::class.java) { apply(d) }
        assertEquals(old, apply(d.copy(resolution = DealResolution.ADD)).deals.first())
    }
    @Test fun titleOnlyAndUnknownPlatformCannotAuthorizeReplacement() {
        for (d in listOf(incoming().copy(sourceItemId = ""), incoming().copy(platform = ""))) {
            assertThrows(ImportValidationException::class.java) {
                apply(d.copy(resolution = DealResolution.REPLACE, existingId = 10, expectedExisting = old))
            }
        }
    }
    @Test fun reviewedSnapshotIsRequiredAndNewOfferDoesNotInheritOldConditions() {
        val replace = incoming().copy(resolution = DealResolution.REPLACE, existingId = 10, expectedExisting = old)
        val result = apply(replace, retainRawText = true).deals.single()
        assertEquals(10L, result.id)
        assertEquals(200L, result.importedAt)
        assertNull(result.observedAt)
        assertEquals("会员券后；有效期10月", result.conditionsText)
        assertEquals("会员券后 ¥29，有效期10月", result.rawText)
        assertFalse(result.priceConfirmed)
        assertThrows(ImportValidationException::class.java) {
            apply(replace, listOf(shop.copy(deals = listOf(old.copy(currentPrice = 40.0)))))
        }
        assertThrows(ImportValidationException::class.java) { apply(replace.copy(expectedExisting = null)) }
    }
    @Test fun explicitKeepLeavesTheOldOfferUntouched() {
        assertEquals(listOf(old), apply(incoming().copy(resolution = DealResolution.KEEP_EXISTING)).deals)
    }
    @Test fun invalidImportedAmountsAreRejected() {
        for (amount in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(ImportValidationException::class.java) { apply(incoming().copy(currentPrice = amount)) }
            assertThrows(ImportValidationException::class.java) {
                applyDraft(emptyList(), ImportDraft(shopName = "店", dishes = listOf(DraftDish("面", amount))), { 1L }, 1L)
            }
        }
    }
    @Test fun conditionalDishPriceIsUnknownUntilExplicitlyConfirmed() {
        val d = Dish(1, "面", 20.0, 12.0, priceConditions = "会员券后", priceConfirmed = false)
        assertNull(d.paid)
        assertEquals(12.0, d.copy(priceConfirmed = true).paid!!, 0.001)
        assertEquals(12.0, Dish(1, "旧手录", 20.0, 12.0).paid!!, 0.001)
    }
    @Test fun ambiguousMenuAmountsRequireEnteringTheApplicablePrice() {
        val rows = ocrLinesToDraftDishes(listOf(OcrLine("牛肉面 19 29"), OcrLine("有效期至10月31日")))
        val d = rows.single()
        assertNull(d.price)
        assertNull(d.groupPrice)
        assertTrue(d.priceConditions.contains("¥19、¥29"))
        assertTrue(d.priceConditions.contains("请填写本次适用价格"))
        assertFalse(d.priceConfirmed)
        // Checking confirmation alone cannot silently promote the second number to the payable price.
        val confirmedOnly = d.copy(priceConfirmed = true)
        val saved = applyDraft(emptyList(), ImportDraft(shopName = "店", dishes = listOf(confirmedOnly)), { 1L }, 1L)
        assertNull(saved.single().dishes.single().paid)
        val corrected = confirmedOnly.copy(price = 19.0)
        val priced = applyDraft(emptyList(), ImportDraft(shopName = "店", dishes = listOf(corrected)), { 1L }, 1L)
        assertEquals(19.0, priced.single().dishes.single().paid!!, 0.001)
        assertTrue(d.rawText.contains("有效期"))
        assertTrue(d.priceConditions.contains("有效期"))
    }
    @Test fun singleUnconditionalMenuPriceAndStableDraftIdentitySurviveEditing() {
        val d = ocrLinesToDraftDishes(listOf(OcrLine("牛肉面 19"))).single()
        assertTrue(d.priceConfirmed)
        assertEquals(19.0, d.price!!, 0.001)
        assertEquals(d.draftId, d.copy(name = "改名").draftId)
    }
    @Test fun evidenceAndUnknownObservationRoundTripWithoutReinterpretingLegacyTime() {
        val data = AppData(listOf(shop.copy(dishes = listOf(Dish(2, "面", 19.0, null, "会员19", "会员", false)),
            deals = listOf(old.copy(importedAt = 200, lastSeenAt = 100, observedAt = null, conditionsText = "会员", priceConfirmed = false)))),
            emptyList(), Settings(), SkipToday())
        assertEquals(data, DataJson.decode(DataJson.encode(data)))
        val legacy = DataJson.decode("""{"shops":[{"id":1,"name":"旧店","dishes":[{"id":2,"name":"面","price":19}],"deals":[{"id":3,"title":"套餐","lastSeenAt":123}]}],"history":[]}""")
        assertTrue(legacy.shops.single().dishes.single().priceConfirmed)
        assertNull(legacy.shops.single().deals.single().observedAt)
        assertEquals(0L, legacy.shops.single().deals.single().importedAt)
        assertFalse(legacy.shops.single().deals.single().priceConfirmed)
    }
    @Test fun recognizedConditionsRemainBesideThePriceAndOutsideTheTitle() {
        val scan = scanDoushengsheng(listOf(
            OcrLine("小店餐厅", 0, 0, 300, 50),
            OcrLine("优惠团购", 0, 70, 300, 100),
            OcrLine("双人套餐", 0, 150, 300, 180),
            OcrLine("会员价 ¥29", 0, 190, 300, 230),
            OcrLine("有效期至10月31日", 0, 240, 300, 270),
        ))
        val deal = scan.deals.single()
        assertEquals("双人套餐", deal.title)
        assertEquals(29.0, deal.currentPrice!!, 0.001)
        assertTrue(deal.conditionsText.contains("会员价"))
        assertTrue(deal.conditionsText.contains("有效期"))
        assertTrue(deal.rawText.contains("会员价 ¥29"))
    }
    @Test fun invalidBackupAmountsFailRatherThanBecomingFreeFood() {
        assertThrows(IllegalArgumentException::class.java) {
            DataJson.decode("""{"shops":[{"id":1,"name":"店","dishes":[{"id":2,"name":"面","price":-1}]}],"history":[]}""")
        }
    }

    @Test fun rawEvidenceStaysInPreviewUnlessRetentionIsExplicitlyEnabled() {
        val d = incoming()
        val dish = DraftDish("面", 19.0, rawText = "会员价19，电话123", priceConditions = "会员", priceConfirmed = false)
        val preview = draft(d).copy(dishes = listOf(dish), rawText = "整页原文")
        val saved = applyDraft(emptyList(), preview, { 1L }, 5L).single()
        assertEquals("", saved.deals.single().rawText)
        assertEquals("", saved.dishes.single().rawText)
        assertEquals(d.conditionsText, saved.deals.single().conditionsText)
        assertEquals("会员", saved.dishes.single().priceConditions)
        assertEquals("整页原文", preview.rawText)
        assertEquals(d.rawText, preview.deals.single().rawText)
        val kept = applyDraft(emptyList(), preview.copy(retainRawText = true), { 1L }, 5L).single()
        assertEquals(d.rawText, kept.deals.single().rawText)
        assertEquals(dish.rawText, kept.dishes.single().rawText)
    }

    @Test fun genericOrUnknownPlatformCannotAuthorizeSkuReplacement() {
        for (platform in listOf("", "other", "dine_in", "unnamed-platform")) {
            val existing = old.copy(platform = platform)
            val d = incoming().copy(platform = platform, resolution = DealResolution.REPLACE,
                existingId = existing.id, expectedExisting = existing)
            assertFalse(replacementAllowed(existing, d, draft(d)))
            assertThrows(ImportValidationException::class.java) { apply(d, listOf(shop.copy(deals = listOf(existing)))) }
        }
    }

    @Test fun inlineValidityStaysEvidenceWithoutDiscardingPricedDish() {
        val d=ocrLinesToDraftDishes(listOf(OcrLine("招牌面 会员价¥19 有效期至10月31日"))).single()
        assertEquals(19.0,d.price!!,0.001)
        assertFalse(d.priceConfirmed)
        assertTrue(d.rawText.contains("有效期"))
        assertTrue(d.priceConditions.contains("有效期"))
    }
}
