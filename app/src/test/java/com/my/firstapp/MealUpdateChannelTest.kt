package com.my.firstapp

import org.junit.Assert.*
import org.junit.Test

class MealUpdateChannelTest {
    private val publicCertificate = "a".repeat(64)
    @Test fun pendingChannelCannotExposeNetworkEndpoint() {
        assertFalse(MealUpdateChannel.Pending.ready)
        assertNull(MealUpdateChannel.Pending.apiUrl)
        assertEquals("com.jintianchishenme.app",MealUpdateChannel.PRODUCTION_PACKAGE)
    }
    @Test fun sourceRepoAndMissingApprovalFailClosed() {
        assertFalse(MealUpdateChannel("h516df/jintian-chishenme",publicCertificate,true).ready)
        assertFalse(MealUpdateChannel("h516df/kaifanla",publicCertificate,true).ready)
        assertFalse(MealUpdateChannel("h516df/example-release",publicCertificate,false).ready)
        assertFalse(MealUpdateChannel("h516df/example-release","",true).ready)
    }
    @Test fun untrustedRoutesAndMalformedCertificateFailClosed() {
        for(repo in listOf("h516df/.","h516df/..","other/release","https://github.com/h516df/release","h516df/release/../../x","h516df/release?token=x")) {
            assertNull(MealUpdateChannel(repo,publicCertificate,true).apiUrl)
        }
        assertFalse(MealUpdateChannel("h516df/example-release","x".repeat(64),true).ready)
        assertEquals("https://api.github.com/repos/h516df/example-release",
            MealUpdateChannel("h516df/example-release",publicCertificate,true).apiUrl)
    }
}
