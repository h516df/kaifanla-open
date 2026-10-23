package com.my.firstapp

import org.junit.Assert.assertEquals
import org.junit.Test

class PaletteTest {
    @Test fun savedThemeIdsRemainAvailableWithDistinctStyles() {
        assertEquals(setOf("strawberry","lavender","peach","mint"), Palettes.map { it.id }.toSet())
        assertEquals(3, Palettes.map { it.style }.toSet().size)
    }
    @Test fun everySemanticPaletteColorIsOpaque() {
        Palettes.forEach { palette ->
            val colors = listOf(palette.primary, palette.deep, palette.light, palette.accent,
                palette.surface, palette.text, palette.subText, palette.line, palette.bgTop, palette.bgBottom) + palette.slices + palette.ring
            colors.forEach { assertEquals("${palette.id}: $it", 1f, it.alpha, 0f) }
        }
    }
}
