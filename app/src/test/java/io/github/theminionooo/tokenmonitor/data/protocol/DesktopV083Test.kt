package io.github.theminionooo.tokenmonitor.data.protocol

import org.junit.Assert.*
import org.junit.Test

class DesktopV083Test {
    private fun accounts(name: String) = listOf("", "worker/").map { prefix ->
        HubProtocolParser.decodeStats(javaClass.getResource("/protocol/v0.83.0/$prefix$name.json")!!.readText()).limits.providers.single()
    }

    @Test fun `Claude legacy tier identifiers arrive as Max and Ultra without relabeling Team`() {
        for ((name, plan) in listOf("max" to "Max", "ultra" to "Ultra", "team" to "Team")) {
            for (account in accounts("claude-$name")) {
                assertEquals(plan, account.plan)
                assertEquals("ok", account.status)
                assertEquals(75.0, account.windows.single().remainingPercent!!, 0.0)
            }
        }
    }

    @Test fun `weekly only Claude quotas do not invent a session window`() {
        for ((name, left) in listOf("weekly" to 65.0, "weekly-zero" to 0.0)) for (account in accounts("claude-$name")) {
            assertEquals(if (left == 0.0) "Max" else "Ultra", account.plan)
            assertEquals(listOf("weekly"), account.windows.map { it.kind })
            assertEquals(left, account.windows.single().remainingPercent!!, 0.0)
        }
        for (account in accounts("claude-missing")) {
            assertNotEquals("ok", account.status)
            assertTrue(account.windows.isEmpty())
        }
    }

    @Test fun `Trae credits preserve authoritative summary and have no invented reset`() {
        for (account in accounts("trae-credits")) {
            val window = account.windows.single()
            assertEquals("CREDITS", window.currency)
            assertEquals(25.0, window.used!!, 0.0)
            assertEquals(75.0, window.remaining!!, 0.0)
            assertEquals(75.0, window.remainingPercent!!, 0.0)
            assertEquals("", window.resetsAt)
        }
    }

    @Test fun `zero and overdrawn Trae balances retain zero without fabricating a percentage`() {
        for (account in accounts("trae-zero")) {
            assertEquals(0.0, account.windows.single().remaining!!, 0.0)
            assertNull(account.windows.single().remainingPercent)
            assertEquals(false, account.windows.single().showMeter)
        }
        for (account in accounts("trae-overdrawn")) {
            assertEquals(125.0, account.windows.single().used!!, 0.0)
            assertEquals(0.0, account.windows.single().remainingPercent!!, 0.0)
        }
        for (account in accounts("trae-expired")) {
            assertEquals("unauthorized", account.status)
            assertTrue(account.windows.isEmpty())
        }
    }

    @Test fun `explicit Claude plan fields take precedence and unrelated account labels stay separate`() {
        fun account(fields: String) = HubProtocolParser.decodeStats("""{"limits":{"providers":[$fields]}}""").limits.providers.single()
        assertEquals("Explicit", account("""{"provider":"claude","plan":"Explicit","accountLabel":"Ultra"}""").plan)
        assertEquals("Explicit", account("""{"provider":"claude","planLabel":"Explicit","accountLabel":"Ultra"}""").plan)
        assertEquals("", account("""{"provider":"custom","accountLabel":"An account"}""").plan)
        assertEquals("", account("""{"provider":"claude"}""").plan)
    }

    @Test fun `released Node and Worker endpoint fixtures remain readable`() {
        for (prefix in listOf("", "worker/")) {
            fun response(name: String) = javaClass.getResource("/protocol/v0.83.0/$prefix$name.json")!!.readText()
            val snapshot = HubProtocolParser.decodeSnapshot(response("health"), response("stats"), response("devices"), response("history"), response("subscriptions"), 0)
            assertTrue(snapshot.health.ok)
            assertEquals(1234L, snapshot.today.totalTokens)
        }
    }
}
