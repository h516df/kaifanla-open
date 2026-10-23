package com.my.firstapp

import org.junit.Assert.*
import org.junit.Test

class MealReelTest {
    @Test fun eachSelectedCandidateStopsAtItsOwnCentre() {
        for(count in 1..40) for(selected in 0 until count) for(start in listOf(0f,1.2f,243.8f)) {
            val end = reelTarget(start,count,selected)
            assertEquals(selected,reelIndex(end.toInt(),count))
            assertTrue(end >= start + count * 5)
        }
    }
    @Test fun neighboursWrapAndEmptyHasNoIndex() {
        assertEquals(2,reelIndex(-1,3))
        assertEquals(0,reelIndex(3,3))
        assertEquals(-1,reelIndex(10,0))
    }
    @Test fun invalidSelectionIsRejectedBeforeAnimation() {
        assertThrows(IllegalArgumentException::class.java) { reelTarget(0f,0,0) }
        assertThrows(IllegalArgumentException::class.java) { reelTarget(0f,3,3) }
    }
}
