package io.github.theminionooo.tokenmonitor.widget

import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.ui.SessionActivityState
import io.github.theminionooo.tokenmonitor.ui.groupHomeSessions
import io.github.theminionooo.tokenmonitor.ui.sessionRowActivity

internal data class NotificationSessions(val running: Int = 0, val waiting: Int = 0) {
    val active: Int get() = running + waiting
}

/** One polling interval of observation grace bridges the 30-second TTL/poll boundary.
 * Explicit idle/finished samples still replace the reading immediately. No grace in dashboard rows.
 */
internal fun notificationSessions(snapshot: HubSnapshot?, now: Long): NotificationSessions {
    if (snapshot == null) return NotificationSessions()
    // Today's copy is more current when a Hub supplies both periods with the same accounting id.
    val todayIds = snapshot.today.sessions.map { it.client to it.id }.toSet()
    val month = snapshot.month.sessions.filter { (it.client to it.id) !in todayIds }
    val states = groupHomeSessions(month, snapshot.today.sessions).map { sessionRowActivity(it, now, observationGraceMs = 30_000L) }
    return NotificationSessions(states.count { it == SessionActivityState.Running }, states.count { it == SessionActivityState.Waiting })
}
