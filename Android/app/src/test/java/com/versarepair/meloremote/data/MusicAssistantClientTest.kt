package com.versarepair.meloremote.data

import com.google.gson.JsonParser
import com.versarepair.meloremote.data.model.MAClientException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicAssistantClientTest {
    private val client = MusicAssistantClient()

    @Test
    fun normalizedServerUrlAddsSchemeAndPreservesPort() {
        val result = MusicAssistantClient.normalizedServerUrl("music.local:8095/")

        assertEquals("http", result.scheme)
        assertEquals("music.local", result.host)
        assertEquals(8095, result.port)
        assertEquals("/", result.encodedPath)
    }

    @Test
    fun normalizedServerUrlRejectsUnsupportedAddresses() {
        assertThrows(MAClientException.InvalidServerAddress::class.java) {
            MusicAssistantClient.normalizedServerUrl("ftp://music.local")
        }
    }

    @Test
    fun loginRequestsCoverCurrentLegacyAndFormPayloads() {
        val requests = client.loginRequests(
            "https://music.example/auth/login".toHttpUrl(),
            "listener+home@example.com",
            "a password",
        )

        assertEquals(3, requests.size)
        val current = JsonParser.parseString(requests[0].bodyText()).asJsonObject
        assertEquals("builtin", current["provider_id"].asString)
        assertEquals("MeloRemote", current["device_name"].asString)
        assertEquals("listener+home@example.com", current["credentials"].asJsonObject["username"].asString)
        assertEquals("a password", current["credentials"].asJsonObject["password"].asString)

        val legacy = JsonParser.parseString(requests[1].bodyText()).asJsonObject
        assertEquals("listener+home@example.com", legacy["username"].asString)
        assertEquals("a password", legacy["password"].asString)

        assertTrue(requests[2].bodyText().contains("username=listener%2Bhome%40example.com"))
        assertTrue(requests[2].bodyText().contains("password=a+password"))
    }
}

private fun okhttp3.Request.bodyText(): String = Buffer().use { buffer ->
    requireNotNull(body).writeTo(buffer)
    buffer.readUtf8()
}
