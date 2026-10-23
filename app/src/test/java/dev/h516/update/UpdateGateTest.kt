package dev.h516.update

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpdateGateTest {
    @Test fun unconfiguredChannelCannotConnectEvenWhenHttpCalledDirectly() {
        var calls = 0
        val http = UpdateHttp { calls++; error("Network must remain disabled") }
        assertFalse(UpdateConfig.configured)
        assertThrows(UpdateFailure::class.java) { http.json("/releases?per_page=20") }
        assertThrows(UpdateFailure::class.java) { http.assetText(1) }
        assertEquals(0,calls)
    }
    @Test fun unconfiguredChannelRejectsAllApiRoutes() {
        assertFalse(UpdateConfig.apiUrl("https://api.github.com/repos/h516df/jintian-chishenme/releases"))
        assertFalse(UpdateConfig.apiUrl("https://api.github.com/repos//releases"))
        assertEquals("com.jintianchishenme.app",UpdateConfig.PACKAGE)
        assertEquals("",UpdateConfig.CERTIFICATE_SHA256)
    }
}
