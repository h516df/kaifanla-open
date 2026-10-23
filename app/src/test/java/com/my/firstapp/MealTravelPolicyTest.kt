package com.my.firstapp

import org.junit.Assert.*
import org.junit.Test

class MealTravelPolicyTest {
    private val dish = Dish(2,"面",null,null)
    private val shop = Shop(1,"小店","🍜",listOf(dish))
    private fun reason(s:Shop=shop,d:Dish=dish,budget:Double?=20.0,walk:Int?=15,
        priceUnknown:Boolean=true,walkUnknown:Boolean=true) = mealBlockReason(s,d,SkipToday(),budget,
        emptyList(),null,walk,priceUnknown,walkUnknown)
    @Test fun unknownsAreExplicitlyOptionalAndNeverBecomeZero() {
        assertNull(reason())
        assertEquals("价格待确认",reason(priceUnknown=false))
        assertEquals("路程未填写",reason(walkUnknown=false))
        assertNull(reason(budget=null,walk=null,priceUnknown=false,walkUnknown=false))
    }
    @Test fun walkingBoundaryUsesOnlyRecordedMinutes() {
        assertNull(reason(s=shop.copy(walkMinutes=15)))
        assertEquals("超出步行时间",reason(s=shop.copy(walkMinutes=16)))
        assertNull(reason(s=shop.copy(walkMinutes=0)))
        assertEquals("路程未填写",reason(s=shop.copy(address="就在附近"),walkUnknown=false))
    }
    @Test fun strictConstraintsMayHaveNoCandidatesAndDoNotRelaxBudget() {
        assertEquals("超预算",reason(d=dish.copy(price=21.0),walkUnknown=false))
        assertTrue(listOf(shop).flatMap { s->s.dishes.filter{reason(s,it,priceUnknown=false,walkUnknown=false)==null} }.isEmpty())
    }
    @Test fun optionalFieldsRoundTripWithoutChangingHistoryOrMenus() {
        val data=AppData(listOf(shop.copy(walkMinutes=12)),listOf(Record(100,"🍜","小店","面",null,null)),
            Settings(maxWalkMinutes=15,allowUnknownPrice=false,allowUnknownWalk=false),SkipToday())
        assertEquals(data,DataJson.decode(DataJson.encode(data)))
    }
    @Test fun oldBackupsKeepUnknownDataUsable() {
        val data=DataJson.decode("""{"shops":[{"id":1,"name":"旧店"}],"history":[],"settings":{}}""")
        assertNull(data.shops.single().walkMinutes)
        assertNull(data.settings.maxWalkMinutes)
        assertTrue(data.settings.allowUnknownPrice)
        assertTrue(data.settings.allowUnknownWalk)
    }
    @Test fun invalidTravelEstimatesAreUnknownRatherThanNearby() {
        for(value in listOf("null","-1","241","1.5","\"10\"","\"nearby\"")) {
            val data=DataJson.decode("""{"shops":[{"id":1,"name":"店","walkMinutes":$value}],"history":[],"settings":{"maxWalkMinutes":$value}}""")
            assertNull(value,data.shops.single().walkMinutes)
            assertNull(value,data.settings.maxWalkMinutes)
        }
    }
}
