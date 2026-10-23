package dev.h516.update

import org.junit.Assert.*
import org.junit.Test

class ReleaseArchiveTest {
    @Test fun rejectDebuggableOrMissingArchiveMetadata() {
        assertFalse(isReleaseArchive(null))
        assertFalse(isReleaseArchive(2))
        assertFalse(isReleaseArchive(3))
        assertTrue(isReleaseArchive(0))
        assertTrue(isReleaseArchive(1))
    }
}
