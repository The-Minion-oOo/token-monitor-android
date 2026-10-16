package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.SessionUsage
import java.time.Instant

/** Why several sessions share one row, following desktop v0.69's Sessions list. */
internal enum class SessionGroupKind { Conversation, AutoReview, GrokBot }

/** A session inside a group row; [main] marks the conversation the group is named after. */
internal data class SessionMember(val session: SessionUsage, val main: Boolean)

/**
 * One line in a session list: a single session or a group. Group totals are the members' sum; members
 * are disjoint accounting records, so nothing is counted twice.
 */
internal data class SessionRow(
    val key: String,
    /** The session that names the row: the most recent record of the main conversation. */
    val display: SessionUsage,
    val kind: SessionGroupKind? = null,
    val members: List<SessionMember> = emptyList(),
    /** Distinct delegated conversations in a [SessionGroupKind.Conversation] group. */
    val subagentCount: Int = 0,
    /** A subagent shown on its own because its parent is not in the list. */
    val subagent: Boolean = false,
    val totalTokens: Long = display.totalTokens,
    val costUsd: Double = display.costUsd,
    val unpricedTokens: Long = display.unpricedTokens,
    val modelTokens: Map<String, Long> = display.modelTokens,
    val sortTime: Long = sessionSortTime(display),
) {
    val grouped: Boolean get() = kind != null
    val sessions: List<SessionUsage> get() = if (grouped) members.map { it.session } else listOf(display)
    val usageMetrics: SessionMetrics = sessionMetrics(sessions)
}

internal fun sessionSortTime(session: SessionUsage): Long =
    runCatching { Instant.parse(session.lastUsedAt.ifBlank { session.startedAt }).toEpochMilli() }.getOrDefault(0L)

private fun SessionUsage.accountingKey() = "$client:$id"

private fun SessionUsage.isBackgroundReview() = sessionKind == "background-review"

/** The parent's key when this session was started by another one, excluding background reviews and self-links. */
private fun SessionUsage.parentKey(): String? {
    if (parentSessionId.isBlank() || isBackgroundReview()) return null
    val parent = parentClient.ifBlank { client }
    return if (parent != client || parentSessionId != id) "$parent:$parentSessionId" else null
}

/** Continuation files of one conversation share a T3 App thread or a verified Codex native id. */
private fun SessionUsage.conversationIdentity(): String = when {
    t3ThreadId.isNotBlank() -> "t3:$t3ThreadId"
    client == "codex" && nativeSessionId.isNotBlank() -> "codex:$nativeSessionId"
    // Its own namespace, so a Codex session id can never alias a native conversation id.
    else -> "session:${accountingKey()}"
}

private val grokBotSandboxId = Regex("^sand-subagent-.+")
private val grokBotModel = Regex("^grok-bot-.+")

private fun List<SessionUsage>.newestFirst(): List<SessionUsage> =
    map { it to sessionSortTime(it) }.sortedByDescending { it.second }.map { it.first }

/** Cursor sandbox ids and a positive grok-bot model mark a bot conversation; an explicit flag wins. */
internal fun isGrokBotSession(session: SessionUsage): Boolean {
    if (session.client != "cursor") return false
    session.grokBotSession?.let { return it }
    return session.id.matches(grokBotSandboxId) ||
        session.modelTokens.any { (model, tokens) -> model.matches(grokBotModel) && tokens > 0 }
}

/**
 * Groups a period's sessions the way desktop v0.69 does: subagents, continuation files and T3 Code runs fold
 * under the conversation that started them while that parent is in the list, background reviews collapse into
 * one Codex Auto Review row and Grok Bot activity records into one Grok Bot row. [evidence] lets other periods
 * prove that a Cursor conversation id belongs to a bot. Rows keep the input order otherwise.
 */
internal fun groupSessions(sessions: List<SessionUsage>, evidence: List<SessionUsage> = emptyList()): List<SessionRow> {
    val botIds = (sessions + evidence).filter(::isGrokBotSession).map { it.id }.toSet()
    val (bots, others) = sessions.partition { it.client == "cursor" && it.id in botIds }
    val (reviews, primary) = others.partition { it.isBackgroundReview() }
    return buildList {
        addAll(groupConversations(primary))
        if (reviews.isNotEmpty()) add(summaryRow("group:codex-auto-review", SessionGroupKind.AutoReview, reviews))
        if (bots.isNotEmpty()) add(summaryRow("group:cursor-grok-bot", SessionGroupKind.GrokBot, bots))
    }
}

private fun groupConversations(sessions: List<SessionUsage>): List<SessionRow> {
    // Independent conversations need neither alias resolution nor parent walks (desktop v0.70). Any link,
    // including a missing or cross-client parent, or a repeated identity takes the full path below.
    val identities = HashSet<String>()
    if (sessions.none { it.parentKey() != null || !identities.add(it.conversationIdentity()) }) {
        return sessions.map { SessionRow(key = it.accountingKey(), display = it) }
    }
    val byKey = LinkedHashMap<String, SessionUsage>()
    sessions.forEach { byKey.putIfAbsent(it.accountingKey(), it) }
    // A conversation may have several accounting records; links resolve through any visible alias.
    val aliases = HashMap<String, SessionUsage>()
    byKey.values.forEach { session ->
        val previous = aliases[session.conversationIdentity()]
        if (previous == null || session.accountingKey() < previous.accountingKey()) aliases[session.conversationIdentity()] = session
    }
    val parentLinks = HashMap<String, Pair<String, String>>()
    val conflicts = HashSet<String>()
    byKey.values.forEach { session ->
        val parentKey = session.parentKey() ?: return@forEach
        val identity = session.conversationIdentity()
        val link = byKey[parentKey]?.conversationIdentity() ?: parentKey
        if (link == identity) return@forEach
        val known = parentLinks[identity]
        if (known != null && known.first != link) conflicts += identity else parentLinks[identity] = link to parentKey
    }
    // Contradictory links never justify collapsing identities.
    fun canonical(session: SessionUsage): SessionUsage =
        if (session.conversationIdentity() in conflicts) session else aliases.getValue(session.conversationIdentity())
    fun rootOf(session: SessionUsage): SessionUsage {
        val seen = HashSet<String>()
        var current = canonical(session)
        while (current.accountingKey() !in seen) {
            val parentKey = (if (current.conversationIdentity() in conflicts) current.parentKey()
                else parentLinks[current.conversationIdentity()]?.second) ?: break
            seen += current.accountingKey()
            val next = canonical(byKey[parentKey] ?: break)
            if (next.accountingKey() in seen) return canonical(session)
            current = next
        }
        return current
    }
    val children = LinkedHashMap<String, MutableList<SessionUsage>>()
    val grouped = HashSet<String>()
    byKey.values.forEach { session ->
        val root = rootOf(session)
        if (root.accountingKey() == session.accountingKey()) return@forEach
        children.getOrPut(root.accountingKey()) { mutableListOf() } += session
        grouped += session.accountingKey()
    }
    // A repeated accounting key keeps its own row rather than disappearing into a group.
    return sessions.mapNotNull { session ->
        val key = session.accountingKey()
        val representative = byKey[key] === session
        when {
            representative && key in grouped -> null
            representative && key in children -> conversationRow(session, children.getValue(key), session.conversationIdentity() in conflicts,
                session.conversationIdentity() !in conflicts && parentLinks[session.conversationIdentity()] != null)
            else -> SessionRow(key = key, display = session, subagent = session.parentKey() != null)
        }
    }
}

private fun conversationRow(parent: SessionUsage, members: List<SessionUsage>, splitIdentity: Boolean, rootSubagent: Boolean): SessionRow {
    val identity = parent.conversationIdentity()
    val all = listOf(parent) + members
    val main = all.filter { it.conversationIdentity() == identity }.newestFirst()
    val delegated = all.filter { it.conversationIdentity() != identity }.newestFirst()
    val ordered = main.map { SessionMember(it, true) } + delegated.map { SessionMember(it, false) }
    return SessionRow(
        // Conflicting links can give two roots one identity; the root's own key keeps their rows apart.
        key = "group:$identity" + if (splitIdentity) ":root:${parent.accountingKey()}" else "",
        display = main.first(),
        kind = SessionGroupKind.Conversation,
        members = ordered,
        subagent = rootSubagent,
        subagentCount = delegated.map { it.conversationIdentity() }.distinct().size,
        totalTokens = all.sumOf { it.totalTokens },
        costUsd = all.sumOf { it.costUsd },
        unpricedTokens = all.sumOf { it.unpricedTokens },
        modelTokens = combinedModels(all),
        sortTime = all.maxOf(::sessionSortTime),
    )
}

private fun summaryRow(key: String, kind: SessionGroupKind, sessions: List<SessionUsage>): SessionRow {
    val ordered = sessions.newestFirst()
    return SessionRow(
        key = key,
        display = ordered.first(),
        kind = kind,
        members = ordered.map { SessionMember(it, false) },
        totalTokens = sessions.sumOf { it.totalTokens },
        costUsd = sessions.sumOf { it.costUsd },
        unpricedTokens = sessions.sumOf { it.unpricedTokens },
        modelTokens = combinedModels(sessions),
        sortTime = sessions.maxOf(::sessionSortTime),
    )
}

private fun combinedModels(sessions: List<SessionUsage>): Map<String, Long> =
    sessions.flatMap { it.modelTokens.entries }.groupBy({ it.key }, { it.value }).mapValues { (_, tokens) -> tokens.sum() }

/** Keeps rows with any matching member, or a group whose own label matches, so a match keeps its whole group. */
internal fun searchSessionRows(rows: List<SessionRow>, searching: Boolean, matches: (SessionUsage) -> Boolean, query: String): List<SessionRow> =
    if (!searching) rows else rows.filter { row ->
        row.sessions.any(matches) || (row.grouped && query.isNotBlank() && sessionRowPresentation(row).title.contains(query.trim(), ignoreCase = true))
    }

/** Running beats waiting, which beats the first member's own state: a group is as busy as its busiest member. */
internal fun sessionRowActivity(row: SessionRow, now: Long): SessionActivityState {
    val states = row.sessions.map { sessionActivityState(it, now) }
    return listOf(SessionActivityState.Running, SessionActivityState.Waiting).firstOrNull { it in states }
        ?: states.firstOrNull() ?: SessionActivityState.Idle
}

/** The row's name and meta line, matching the desktop's group labels. */
internal fun sessionRowPresentation(row: SessionRow, showTitles: Boolean = true): SessionRowLabels = when (row.kind) {
    SessionGroupKind.AutoReview -> SessionRowLabels("Codex Auto Review", "${row.members.size} background runs")
    SessionGroupKind.GrokBot -> SessionRowLabels("Grok Bot", "${row.members.size} activity records")
    SessionGroupKind.Conversation -> {
        val latest = row.sessions.maxByOrNull(::sessionSortTime) ?: row.display
        val labels = sessionRowLabels(row.display.copy(modelNames = topModelFirst(row), lastUsedAt = latest.lastUsedAt.ifBlank { latest.startedAt },
            messageCount = 0), showTitles)
        val count = row.subagentCount.takeIf { it > 0 }?.let { if (it == 1) "1 subagent" else "$it subagents" }
        // The count leads, so a narrow row shortens the project rather than hiding how many subagents ran.
        val messages = row.usageMetrics.messageCount.takeIf { it > 0 }?.let { "$it messages" }
        SessionRowLabels(labels.title, listOfNotNull(count, "Subagent".takeIf { row.subagent }, labels.meta, messages).joinToString(" · "))
    }
    null -> sessionRowLabels(row.display, showTitles).let { labels ->
        if (row.subagent) labels.copy(meta = "Subagent · ${labels.meta}") else labels
    }
}

/** An untitled group is named by its client and the model behind most of its tokens. */
private fun topModelFirst(row: SessionRow): List<String> =
    row.modelTokens.entries.sortedByDescending { it.value }.map { it.key }.ifEmpty { row.display.modelNames }
