package com.my.firstapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuckyWheelTest {
    @Test
    fun labelsStayUprightAcrossWheel() {
        assertFalse(shouldFlipWheelLabel(0f, 0f))
        assertFalse(shouldFlipWheelLabel(0f, 90f))
        assertTrue(shouldFlipWheelLabel(0f, 180f))
        assertFalse(shouldFlipWheelLabel(0f, 270f))
        assertTrue(shouldFlipWheelLabel(45f, 90f))
        assertFalse(shouldFlipWheelLabel(270f, 90f))
        assertTrue(shouldFlipWheelLabel(-90f, 270f))
    }
}
