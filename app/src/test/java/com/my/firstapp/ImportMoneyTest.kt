package com.my.firstapp
import org.junit.Assert.*
import org.junit.Test
class ImportMoneyTest {
    @Test fun onlyFiniteNonnegativeOrExplicitUnknown() {
        assertTrue(validImportMoney(null)); assertTrue(validImportMoney(0.0)); assertTrue(validImportMoney(19.9))
        for(value in listOf(-1.0,Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY))assertFalse(validImportMoney(value))
    }
    @Test fun observationDateIsOptionalAndStrict() {
        assertNull(parseObservedDate(""))
        assertEquals(-1L,parseObservedDate("2026-02-30"))
        assertEquals(-1L,parseObservedDate("yesterday"))
        assertTrue(parseObservedDate("2026-10-19")!!>0)
    }
}
