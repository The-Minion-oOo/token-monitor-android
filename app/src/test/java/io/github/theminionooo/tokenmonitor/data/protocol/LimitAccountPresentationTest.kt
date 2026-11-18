package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.ui.alibabaRegionLabel
import io.github.theminionooo.tokenmonitor.ui.limitAccountIdentity
import io.github.theminionooo.tokenmonitor.ui.limitWindowTitle
import org.junit.Assert.*
import org.junit.Test

class LimitAccountPresentationTest {
    @Test fun `preview Hub keeps sibling accounts and separate quota products`() {
        fun fixture(name: String) = javaClass.getResource("/protocol/upcoming-98603b6d/$name.json")!!.readText()
        val stats = HubProtocolParser.decodeStats(fixture("limits"))
        val presentation = requireNotNull(decodeLimitAccountPresentation(fixture("presentation")))
        assertEquals(presentation.revision, stats.limitAccountPresentationRevision)
        val accounts = presentation.applyNames(presentation.applyOrder(stats.limits.providers))
        val deepseek = accounts.filter { it.provider == "deepseek" }
        assertEquals(listOf("Work", "Personal"), deepseek.map { it.accountName })
        assertEquals(listOf("unavailable", "ok"), deepseek.map { it.status })
        assertTrue(deepseek.first().windows.isEmpty())
        assertEquals(24.0, deepseek.last().windows.single().remaining)

        val alibaba = accounts.filter { it.provider == "alibaba" }
        assertEquals(1, alibaba.map { it.accountKey }.distinct().size)
        assertEquals(2, alibaba.map(::limitAccountIdentity).distinct().size)
        assertEquals(setOf("Bailian Personal", "Model Studio Team"), alibaba.map(::alibabaRegionLabel).toSet())

        val opencode = accounts.single { it.provider == "opencode" }
        assertEquals(listOf("go", "zen"), opencode.windows.map { it.productId })
        assertEquals(listOf("Go · Weekly", "Zen · Balance"), opencode.windows.map { limitWindowTitle(opencode, it) })
        assertEquals(stats.limits.providers.map { it.provider }, accounts.map { it.provider })
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
