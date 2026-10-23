package com.my.firstapp

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class ImportSessionSafetyTest {
    @Test fun oldPageCannotEditOrCommitNewSession() {
        val vm=AppViewModel(RuntimeEnvironment.getApplication())
        vm.openImport(ImportDraft(shopName="第一店",dishes=listOf(DraftDish("旧菜",12.0))))
        val old=vm.importDraft!!;val oldSession=vm.importSessionId
        vm.openImport(ImportDraft(shopName="第二店",dishes=listOf(DraftDish("新菜",13.0))))
        vm.updateImportDraft(old,oldSession)
        assertEquals("第二店",vm.importDraft!!.shopName)
        assertFalse(vm.confirmImport(old,oldSession))
        vm.cancelImport(oldSession)
        assertEquals("第二店",vm.importDraft!!.shopName)
    }
    @Test fun unconfirmedPriceIsNotRecordedAsPaid() {
        val vm=AppViewModel(RuntimeEnvironment.getApplication())
        val dish=Dish(999,"会员套餐",30.0,9.9,priceConditions="仅限新会员",priceConfirmed=false)
        assertTrue(vm.addRecord(Shop(998,"测试店","🍜",listOf(dish)),dish))
        assertNull(vm.history.first().price);assertNull(vm.history.first().groupPrice)
    }
    @Test fun invalidDraftDoesNotMutateMenus() {
        val vm=AppViewModel(RuntimeEnvironment.getApplication())
        val before=vm.shops
        vm.openImport(ImportDraft(shopName="异常店",dishes=listOf(DraftDish("坏金额",Double.NaN))))
        assertFalse(vm.confirmImport(vm.importDraft!!))
        assertEquals(before,vm.shops);assertNotNull(vm.importDraft)
    }
    @Test fun pasteNeverGuessesWhichOfTwoAmountsApplies() {
        val vm=AppViewModel(RuntimeEnvironment.getApplication())
        vm.startPasteImport("招牌面 30 12\n仅限会员券后价")
        val draft=vm.importDraft!!
        assertEquals("paste",draft.source)
        assertTrue(draft.rawText.contains("仅限会员"))
        val dish=draft.dishes.first()
        assertNull(dish.price);assertNull(dish.groupPrice)
        assertFalse(dish.priceConfirmed)
    }
    @Test fun platformChangeRequiresFreshPriceConfirmation() {
        val vm=AppViewModel(RuntimeEnvironment.getApplication())
        vm.openImport(ImportDraft(shopName="合成店",platform="meituan",deals=listOf(
            DraftDeal("双人餐",29.0,platform="meituan",priceConfirmed=true))))
        vm.updateImportDraft(vm.importDraft!!.copy(platform="eleme"))
        assertEquals("eleme",vm.importDraft!!.deals.single().platform)
        assertFalse(vm.importDraft!!.deals.single().priceConfirmed)
    }
}
