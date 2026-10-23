package com.my.firstapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PickVarietyTest {
    private fun shop(id: Long, dishCount: Int) =
        Shop(id, "店$id", "🍜", List(dishCount) { Dish(id * 100 + it, "菜${id}_$it", 10.0, null) })

    private fun groups(vararg shops: Shop) = shops.map { it to it.dishes }

    @Test
    fun takesSevenFromDifferentShopsWhenPossible() {
        val g = groups(*Array(10) { shop(it.toLong(), 5) })
        repeat(20) { seed ->
            val picked = pickVariety(g, 7, Random(seed))
            assertEquals(7, picked.size)
            assertEquals("店不能重样", 7, picked.map { it.first.id }.distinct().size)
        }
    }

    @Test
    fun fewShopsStillFillsUpToSeven() {
        val picked = pickVariety(groups(shop(1, 10), shop(2, 10)), 7, Random(1))
        assertEquals(7, picked.size)
        assertEquals("吃的不能重复", 7, picked.map { it.second.id }.distinct().size)
        // 两家店轮流拿，数量最多差 1
        val counts = picked.groupingBy { it.first.id }.eachCount().values
        assertTrue(counts.max() - counts.min() <= 1)
    }

    @Test
    fun fewerDishesThanSevenReturnsAll() {
        val picked = pickVariety(groups(shop(1, 2), shop(2, 1), shop(3, 0)), 7, Random(1))
        assertEquals(3, picked.size)
    }

    @Test
    fun differentSeedsGiveDifferentBatches() {
        val g = groups(*Array(10) { shop(it.toLong(), 5) })
        val batches = (0 until 5).map { pickVariety(g, 7, Random(it)).map { p -> p.second.id }.toSet() }
        assertTrue("换一批应该真的换", batches.distinct().size > 1)
    }
}
