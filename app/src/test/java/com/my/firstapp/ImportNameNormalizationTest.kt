package com.my.firstapp

import org.junit.Assert.*
import org.junit.Test

class ImportNameNormalizationTest {
    private fun apply(old:List<Dish>, incoming:List<DraftDish>):List<Dish> {
        var id=100L
        return applyDraft(listOf(Shop(1,"测试店","🍜",old)),
            ImportDraft(shopName="测试店",dishes=incoming),{id++},1L).single().dishes
    }
    @Test fun paddedEditedNameDoesNotDuplicateOrOverwriteKnownPrice() {
        val old=Dish(2,"招牌面",19.0,null)
        assertEquals(listOf(old),apply(listOf(old),listOf(DraftDish("  招牌面 ",9.0))))
    }
    @Test fun paddedNameFillsUnknownPriceWithoutCreatingSecondCandidate() {
        val old=Dish(2,"招牌面",null,null)
        val saved=apply(listOf(old),listOf(DraftDish("招牌面 ",9.0,priceConditions="会员价",priceConfirmed=false)))
        assertEquals(1,saved.size);assertEquals(2L,saved.single().id)
        assertEquals(9.0,saved.single().price!!,0.001);assertNull(saved.single().paid)
    }
    @Test fun paddingVariantsWithinOneBatchRemainOneDish() {
        val saved=apply(emptyList(),listOf(DraftDish("招牌面",19.0),DraftDish(" 招牌面 ",9.0)))
        assertEquals(1,saved.size);assertEquals(19.0,saved.single().price!!,0.001)
    }
    @Test fun normalizationDoesNotFoldWidthCaseOrInternalSpacing() {
        val names=listOf("A面","a面","Ａ面","招牌 面","招牌面")
        assertEquals(names,apply(emptyList(),names.map{DraftDish(it,10.0)}).map{it.name})
    }
    @Test fun reverseBatchOrderAlsoRemainsOneDish() {
        val saved=apply(emptyList(),listOf(DraftDish(" 招牌面 ",19.0),DraftDish("招牌面",9.0)))
        assertEquals(1,saved.size);assertEquals("招牌面",saved.single().name)
    }
    @Test fun previewMatchingUsesTheSameTrimRule() {
        val old=Dish(2,"招牌面",19.0,null)
        for(name in listOf(" 招牌面 ","\t招牌面\n","　招牌面　")) {
            val draft=attachToShops(listOf(Shop(1,"测试店","🍜",listOf(old))),
                ImportDraft(shopName="测试店",dishes=listOf(DraftDish(name,9.0))))
            assertTrue(draft.dishes.single().existing)
            assertEquals(listOf(old),apply(listOf(old),draft.dishes))
        }
    }
    @Test fun blankRejectedAndUncheckedDishIgnored() {
        assertThrows(ImportValidationException::class.java) { apply(emptyList(),listOf(DraftDish("　 \t",9.0))) }
        assertTrue(apply(emptyList(),listOf(DraftDish("招牌面 ",9.0,checked=false))).isEmpty())
    }
    @Test fun legacyStoredNamesAreNotRenamedOrCleanedUp() {
        val legacy=Dish(2," 招牌面 ",19.0,null)
        val saved=apply(listOf(legacy),listOf(DraftDish("其他菜",10.0)))
        assertEquals(legacy,saved.first())
    }
}
