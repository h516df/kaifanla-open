package com.my.firstapp

import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImportDraftEditingTest {
    private fun vm() = AppViewModel(RuntimeEnvironment.getApplication())

    @Test fun editingKeepsChosenIdsAndAllFieldsUntilConfirmation() {
        val vm = vm()
        val shop = vm.shops.first()
        assertTrue(vm.saveDeal(shop.id, null, "午餐", 12.0, 20.0))
        val dealId = vm.shops.first { it.id == shop.id }.deals.first { it.title == "午餐" }.id
        vm.openImport(ImportDraft(shopName = shop.name, matchedShopId = shop.id,
            deals = listOf(DraftDeal("午餐", 12.0, 20.0, existingId = dealId)),
            dishes = listOf(DraftDish("米饭", 2.0))))
        val session = vm.importSessionId
        val edit = vm.importDraft!!.copy(shopName = "EditedShop", address = "EditedAddress",
            deals = listOf(vm.importDraft!!.deals.single().copy(title = "EditedDeal", currentPrice = 18.6, checked = false)),
            dishes = listOf(vm.importDraft!!.dishes.single().copy(name = "EditedRice", price = 3.0)))
        vm.updateImportDraft(edit)
        assertEquals(edit, vm.importDraft)
        assertEquals(shop.id, vm.importDraft!!.matchedShopId)
        assertNull(vm.importDraft!!.deals.single().existingId)
        assertEquals(dealId, vm.importDraft!!.deals.single().suspectedExistingId)
        assertEquals(session, vm.importSessionId)
        assertEquals(shop.name, vm.shops.first().name)
    }

    @Test fun cancelledDraftCannotBeReopenedByLateEditingAndNewImportHasFreshSession() {
        val vm = vm()
        vm.openImport(ImportDraft(shopName = "第一餐厅", dishes = listOf(DraftDish("Rice"))))
        val old = vm.importDraft!!
        val session = vm.importSessionId
        vm.cancelImport()
        vm.updateImportDraft(old.copy(shopName = "LateEdit"))
        assertNull(vm.importDraft)
        vm.openImport(ImportDraft(shopName = "第二餐厅", dishes = listOf(DraftDish("Noodles"))))
        assertTrue(vm.importSessionId > session)
        assertEquals("第二餐厅", vm.importDraft!!.shopName)
    }
}

