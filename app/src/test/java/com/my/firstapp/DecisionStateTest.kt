package com.my.firstapp

import org.junit.Assert.*
import org.junit.Test

class DecisionStateTest {
    private val dish = Dish(2, "Noodles", 20.0, 15.0)
    private val shop = Shop(1, "Kitchen", "🍜", listOf(dish))
    private val meal = DecisionResult.Meal(shop, dish)

    @Test fun modeChangeClearsSelectedShop() {
        assertNull(DecisionState(shopId = 1).changeMode(true).shopId)
    }
    @Test fun spinningBlocksModeShopAndBatchChanges() {
        val state = DecisionState(shopId = 1).beginSpin()
        assertEquals(state, state.changeMode(true))
        assertEquals(state, state.chooseShop(null))
        assertEquals(state, state.nextBatch())
    }
    @Test fun candidateSnapshotKeepsOriginalChoice() {
        val snapshot = DecisionCandidates(listOf(shop), null, emptyList(), listOf(shop to dish), true)
        assertEquals(meal, snapshot.result(0))
        assertNull(snapshot.result(1))
    }
    @Test fun emptyCandidateSetNeverProducesResult() {
        val candidates = DecisionCandidates(emptyList(), null, emptyList(), emptyList(), true)
        assertEquals(0, candidates.count)
        assertNull(candidates.result(0))
    }
    @Test fun confirmedMealIsConsumedOnlyOnce() {
        val saved = DecisionState().showResult(meal).mealSaved()
        assertEquals(1, saved.batch)
        assertEquals(saved, saved.mealSaved())
        assertNull(saved.result)
    }
    @Test fun dismissalDoesNotRecordOrAdvanceBatch() {
        val dismissed = DecisionState().showResult(meal).dismissResult()
        assertEquals(0, dismissed.batch)
        assertEquals(dismissed, dismissed.mealSaved())
    }
    @Test fun cancellationAllowsNextSpin() {
        val cancelled = DecisionState().beginSpin().cancelSpin()
        assertFalse(cancelled.spinning)
        assertNull(cancelled.result)
        assertTrue(cancelled.beginSpin().spinning)
    }
    @Test fun recreationRestoresNavigationButNeverReplaysResultOrAnimation() {
        val state = DecisionState(true, null, 3).beginSpin()
        val restored = DecisionState.restore(state.checkpoint())
        assertTrue(restored.direct)
        assertEquals(3, restored.batch)
        assertFalse(restored.spinning)
        assertNull(restored.result)
        assertNull(DecisionState.restore(state.finishSpin(meal).checkpoint()).result)
    }
}
