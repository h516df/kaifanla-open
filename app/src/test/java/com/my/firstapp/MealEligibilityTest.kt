package com.my.firstapp

import org.junit.Assert.*
import org.junit.Test

class MealEligibilityTest {
    private val dish = Dish(2, "Noodles", 25.0, 15.0)
    private val shop = Shop(1, "Kitchen", "🍜", listOf(dish))
    private fun reason(d: Dish = dish, skip: SkipToday = SkipToday(), budget: Double? = null,
        records: List<Record> = emptyList(), cutoff: Long? = null) =
        mealBlockReason(shop, d, skip, budget, records, cutoff)
    @Test fun usesDealPriceAndIncludesExactBudget() {
        assertNull(reason(budget = 15.0))
        assertEquals("超预算", reason(budget = 14.99))
    }
    @Test fun noBudgetAndUnknownPriceRemainEligible() {
        assertNull(reason())
        assertNull(reason(dish.copy(price = null, groupPrice = null), budget = 0.0))
    }
    @Test fun skipTakesPriorityOverBudgetAndRecent() {
        assertEquals("今天不想吃", reason(skip = SkipToday(shopIds=setOf(1)), budget=0.0))
        assertEquals("今天不想吃", reason(skip = SkipToday(dishIds=setOf(2))))
    }
    @Test fun recentBoundaryInclusiveAndShopNameMatters() {
        val record = Record(1000,"🍜","Kitchen","Noodles",25.0,15.0)
        assertEquals("最近吃过", reason(records=listOf(record),cutoff=1000))
        assertNull(reason(records=listOf(record),cutoff=1001))
        assertNull(reason(records=listOf(record.copy(shopName="Other")),cutoff=1000))
        assertNull(reason(records=listOf(record),cutoff=null))
    }
    @Test fun restrictiveBudgetCanProduceAnEmptyCandidateList() {
        val dishes = shop.dishes.filter { reason(it,budget=1.0)==null }
        assertTrue(dishes.isEmpty())
    }
    @Test fun extractedPolicyMatchesPreviousImplementationAcrossFixtures() {
        fun previous(s: Shop, d: Dish, skip: SkipToday, budget: Double?, history: List<Record>, cutoff: Long?): String? {
            if (s.id in skip.shopIds || d.id in skip.dishIds) return "今天不想吃"
            val paid = d.paid
            if (budget != null && paid != null && paid > budget) return "超预算"
            if (cutoff != null && history.any { it.time >= cutoff && it.shopName == s.name && it.dishName == d.name }) return "最近吃过"
            return null
        }
        val histories = listOf(emptyList(), listOf(Record(1000,"🍜","Kitchen","Noodles",25.0,15.0)))
        for (price in listOf(null, 0.0, 10.0, 25.0))
            for (deal in listOf(null, 0.0, 15.0))
                for (budget in listOf(null, 0.0, 14.99, 15.0, 30.0))
                    for (skip in listOf(SkipToday(), SkipToday(shopIds=setOf(1)), SkipToday(dishIds=setOf(2))))
                        for (history in histories)
                            for (cutoff in listOf(null, 999L, 1000L, 1001L)) {
                                val candidate = dish.copy(price=price, groupPrice=deal)
                                assertEquals(previous(shop,candidate,skip,budget,history,cutoff),
                                    mealBlockReason(shop,candidate,skip,budget,history,cutoff))
                            }
    }
}
