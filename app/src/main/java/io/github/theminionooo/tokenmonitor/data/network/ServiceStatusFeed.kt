package io.github.theminionooo.tokenmonitor.data.network

import io.github.theminionooo.tokenmonitor.domain.ServiceHealth
import io.github.theminionooo.tokenmonitor.domain.ServiceProviderStatus
import java.io.StringReader
import java.time.Instant
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

private data class FeedEntry(val title: String, val updated: String, val summary: String)

/** No DTDs, external entities, HTML fetches or provider credentials are needed. */
private fun readFeed(body: String): List<FeedEntry> {
    require(body.length <= 1_048_576 && !Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(body))
    val entries = mutableListOf<FeedEntry>()
    val path = mutableListOf<String>()
    var title = StringBuilder()
    var updated = StringBuilder()
    var summary = StringBuilder()
    val handler = object : DefaultHandler() {
        override fun resolveEntity(publicId: String?, systemId: String?): InputSource = throw SAXException("External entity")
        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            require(path.size < 16)
            if (path.isEmpty()) require(localName == "feed" && uri == "http://www.w3.org/2005/Atom")
            if ((path == listOf("feed") && localName == "entry") ||
                (path == listOf("feed", "entry") && localName in setOf("title", "updated", "summary"))) {
                require(uri == "http://www.w3.org/2005/Atom")
            }
            path.add(localName)
            if (path == listOf("feed", "entry")) {
                require(entries.size < 4096)
                title = StringBuilder(); updated = StringBuilder(); summary = StringBuilder()
            }
        }
        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (path.size == 3 && path[1] == "entry") when (path[2]) {
                "title" -> title.append(ch, start, length)
                "updated" -> updated.append(ch, start, length)
                "summary" -> summary.append(ch, start, length)
            }
        }
        override fun endElement(uri: String, localName: String, qName: String) {
            if (path == listOf("feed", "entry")) entries.add(FeedEntry(title.toString().trim(), updated.toString().trim(), summary.toString()))
            path.removeAt(path.lastIndex)
        }
        override fun error(e: org.xml.sax.SAXParseException) { throw e }
        override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
    }
    val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
    factory.newSAXParser().parse(InputSource(StringReader(body)), handler)
    require(entries.isNotEmpty())
    return entries
}

internal fun summarizeAtomFeedProvider(provider: ServiceStatusProvider, body: String, checkedAt: Long): ServiceProviderStatus = try {
    val entries = readFeed(body).sortedByDescending { runCatching { Instant.parse(it.updated).toEpochMilli() }.getOrDefault(0) }
    fun status(entry: FeedEntry) = Regex("Status:</strong>\\s*([a-z_]+)", RegexOption.IGNORE_CASE)
        .find(entry.summary)?.groupValues?.get(1)?.lowercase().orEmpty()
    val active = entries.filter { status(it) !in setOf("resolved", "completed", "postmortem", "canceled", "cancelled") }
    val maintenance = active.filter { Regex("maintenance|维护", RegexOption.IGNORE_CASE).containsMatchIn(it.title) }
    val incidents = active - maintenance.toSet()
    val outage = incidents.any { Regex("outage|unavailable|not available|中断|不可用|宕机", RegexOption.IGNORE_CASE).containsMatchIn(it.title) }
    val affected = incidents.flatMap {
        Regex("Affected components:</strong>([^<]*)").find(it.summary)?.groupValues?.get(1)
            .orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
    }.distinct().size
    ServiceProviderStatus(
        id = provider.id, label = provider.label, pageUrl = provider.pageUrl,
        health = if (outage) ServiceHealth.Outage else if (incidents.isNotEmpty()) ServiceHealth.Degraded else ServiceHealth.Ok,
        description = incidents.firstOrNull()?.title?.takeIf(String::isNotBlank)
            ?: if (incidents.isNotEmpty()) "Degraded Performance" else "All Systems Operational",
        affectedComponents = affected, incidentCount = incidents.size, maintenanceCount = maintenance.size, checkedAt = checkedAt,
    )
} catch (_: Exception) {
    unknownProvider(provider, checkedAt)
}
