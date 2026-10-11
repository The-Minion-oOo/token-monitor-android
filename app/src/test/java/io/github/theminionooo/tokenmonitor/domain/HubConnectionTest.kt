package io.github.theminionooo.tokenmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubConnectionTest {
    @Test fun `text form never contains the secret`() {
        val connection = HubConnection("http://100.64.0.1:17321", "synthetic-secret-value", true, "http://192.168.1.2:17321")
        val text = connection.toString()
        assertFalse(text.contains("synthetic-secret-value"))
        assertFalse(listOf(connection).toString().contains("synthetic-secret-value"))
        assertTrue(text.contains("http://100.64.0.1:17321"))
        assertTrue(text.contains("http://192.168.1.2:17321"))
        assertTrue(text.contains("allowLocalNetwork=true"))
    }

    @Test fun `equality and copy still use the secret`() {
        val connection = HubConnection("http://100.64.0.1:17321", "one", false)
        assertEquals(connection, connection.copy())
        assertFalse(connection == connection.copy(secret = "two"))
    }
}
