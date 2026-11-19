package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.ui.alibabaRegionLabel
import io.github.theminionooo.tokenmonitor.ui.limitAccountIdentity
import io.github.theminionooo.tokenmonitor.ui.limitWindowTitle
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class LimitAccountPresentationTest {
    @Test fun `saved Hub responses keep sibling accounts and separate quota products`() {
        for (prefix in listOf("", "worker/")) {
            checkAccounts { name ->
                requireNotNull(javaClass.getResource("/protocol/v0.94.0/$prefix$name.json")).readText()
            }
        }
    }

    @Test fun `released Node and Worker preserve account presentation`() {
        val directory = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")
        assumeTrue("Supply responses from the pinned Hub contract harness", !directory.isNullOrBlank())
        for (prefix in listOf("", "worker/")) {
            checkAccounts { name -> File(requireNotNull(directory), "$prefix$name.json").readText() }
        }
    }

    private fun checkAccounts(read: (String) -> String) {
        val stats = HubProtocolParser.decodeStats(read("limit-account-stats"))
        val presentation = requireNotNull(decodeLimitAccountPresentation(read("limit-account-presentation")))
        assertEquals(presentation.revision, stats.limitAccountPresentationRevision)
        val providers = stats.limits.providers.filter {
            it.sourceDeviceId in setOf("synthetic-quota-desktop", "synthetic-quota-laptop")
        }
        val accounts = presentation.applyNames(presentation.applyOrder(providers))
        val deepseek = accounts.filter { it.provider == "deepseek" }
        assertEquals(listOf("Work", "Personal"), deepseek.map { it.accountName })
        assertEquals(listOf("unavailable", "ok"), deepseek.map { it.status })
        assertTrue(deepseek.first().windows.isEmpty())
        assertEquals(24.0, deepseek.last().windows.single().remaining)

        val factory = accounts.filter { it.provider == "factory" }
        assertEquals(listOf("Solo", "Team"), factory.map { it.accountName })
        assertEquals(listOf(10.0, 30.0), factory.map { it.windows.single().usedPercent })

        val alibaba = accounts.filter { it.provider == "alibaba" }
        assertEquals(1, alibaba.map { it.accountKey }.distinct().size)
        assertEquals(2, alibaba.map(::limitAccountIdentity).distinct().size)
        assertEquals(setOf("Bailian Personal", "Model Studio Team"), alibaba.map(::alibabaRegionLabel).toSet())

        val opencode = accounts.single { it.provider == "opencode" }
        assertEquals(listOf("go", "zen"), opencode.windows.map { it.productId })
        assertEquals(listOf("Go · Weekly", "Zen · Balance"), opencode.windows.map { limitWindowTitle(opencode, it) })
        assertEquals(providers.map { it.provider }, accounts.map { it.provider })
    }

    @Test fun `absent or malformed additions fall back to reported names and previous window labels`() {
        val stats = HubProtocolParser.decodeStats("""
            {"syncSettingsRevisions":{"limitAccountPresentation":"1"},"limits":{"providers":[
              {"provider":"opencode","accountKey":"a","accountName":"Hub name","windows":[
                {"kind":"weekly","label":"Weekly"},{"kind":"billing","label":"Balance","productId":"not a product"}]}]}}
        """.trimIndent())
        val account = stats.limits.providers.single()
        assertNull(stats.limitAccountPresentationRevision)
        assertEquals(listOf("", ""), account.windows.map { it.productId })
        assertEquals("Balance", limitWindowTitle(account, account.windows.last()))

        for (label in listOf("\"\"", "true", "\"test@example.com\"", "\"https://example.com\"")) {
            val document = """{"ok":true,"version":1,"revision":1,"value":{"opencode":{"labels":{"a":$label},"order":[]}}}"""
            assertEquals(account, requireNotNull(decodeLimitAccountPresentation(document)).applyNames(listOf(account)).single())
        }
        assertNull(decodeLimitAccountPresentation("""{"ok":true,"version":1,"revision":"1","value":null}"""))
    }
}
