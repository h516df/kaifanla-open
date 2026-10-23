package com.my.firstapp

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoBackupTest {
    @Test
    fun namesAndRetentionAreStable() {
        assertEquals("今天吃什么备份_2026-10-02.json", autoBackupFileName("2026-10-02"))
        val rows = listOf(
            1L to "今天吃什么备份_2026-09-30.json",
            2L to "今天吃什么备份_2026-10-02.json",
            3L to "别的文件.json",
            4L to "今天吃什么备份_2026-10-01.json",
        )
        assertEquals(listOf(1L), backupIdsToDelete(rows, keep = 2))
    }
}
