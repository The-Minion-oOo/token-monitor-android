package io.github.theminionooo.tokenmonitor.data.network

import io.github.theminionooo.tokenmonitor.domain.ServiceHealth
import io.github.theminionooo.tokenmonitor.domain.ServiceProviderStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun summarizeJsonStatus(provider: ServiceStatusProvider, payload: JsonObject, checkedAt: Long): ServiceProviderStatus =
    when (provider.format) {
        ServiceStatusFormat.Statuspage -> summarizeStatuspageProvider(provider, payload, checkedAt)
        ServiceStatusFormat.Instatus -> summarizeInstatus(provider, payload, checkedAt)
        ServiceStatusFormat.OpenCode -> summarizeOpenCode(provider, payload, checkedAt)
        ServiceStatusFormat.Atom -> error("Atom feeds use the XML reader")
    }

private enum class Impact(val health: ServiceHealth, val description: String) {
    None(ServiceHealth.Ok, "All Systems Operational"),
    Minor(ServiceHealth.Degraded, "Degraded Performance"),
    Major(ServiceHealth.Outage, "Partial Service Outage"),
    Critical(ServiceHealth.Outage, "Major Outage"),
}

internal fun summarizeStatuspageProvider(
    provider: ServiceStatusProvider,
    payload: JsonObject,
    checkedAt: Long,
): ServiceProviderStatus {
    val status = payload["status"] as? JsonObject
    val components = payload.objects("components")
    val incidents = payload.objects("incidents").activeExcept(inactiveIncidents)
    val maintenance = payload.objects("scheduled_maintenances").activeExcept(inactiveMaintenance)
    val scope = provider.componentIds
    if (scope.isEmpty()) {
        val impact = statuspageImpacts[status?.text("indicator")?.lowercase()]
        return ServiceProviderStatus(
            id = provider.id,
            label = provider.label,
            pageUrl = provider.pageUrl,
            health = impact?.health ?: ServiceHealth.Unknown,
            description = incidents.firstOrNull()?.text("name").orEmpty().ifBlank {
                status?.text("description").orEmpty().ifBlank { "Unknown" }
            },
            affectedComponents = components.count { it.text("status").lowercase() !in operationalComponents },
            incidentCount = incidents.size,
            maintenanceCount = maintenance.size,
            checkedAt = checkedAt,
        )
    }

    val selected = components.filter { it.text("id") in scope }
    if (selected.map { it.text("id") }.toSet() != scope) return unknownProvider(provider, checkedAt)
    fun affectsProduct(item: JsonObject) = item.objects("components").any { it.text("id") in scope }
    val scopedIncidents = incidents.filter(::affectsProduct)
    val affected = selected.map {
        when (it.text("status").lowercase()) {
            in operationalComponents -> Impact.None
            "major_outage" -> Impact.Critical
            "partial_outage" -> Impact.Major
            else -> Impact.Minor
        }
    }
    val worst = (affected + scopedIncidents.map {
        statuspageImpacts[it.text("impact").lowercase()] ?: Impact.Minor
    }).maxOrNull() ?: Impact.None
    return summary(provider, checkedAt, worst,
        description = scopedIncidents.firstOrNull()?.text("name").orEmpty().ifBlank { worst.description },
        affected = affected.count { it != Impact.None }, incidents = scopedIncidents.size,
        maintenance = maintenance.count(::affectsProduct))
}

private fun summarizeInstatus(provider: ServiceStatusProvider, payload: JsonObject, checkedAt: Long): ServiceProviderStatus {
    val page = payload["page"] as? JsonObject ?: error("Missing status page")
    val pageImpact = instatusPageImpacts[page.text("status").lowercase()] ?: error("Unknown status page")
    val incidents = payload.strictObjects("activeIncidents").map { incident ->
        val impact = when (incident.text("impact").lowercase()) {
            "majoroutage" -> Impact.Critical
            "partialoutage" -> Impact.Major
            else -> Impact.Minor
        }
        impact to incident.text("name")
    }
    val worst = maxOf(pageImpact, incidents.maxOfOrNull { it.first } ?: Impact.None)
    val headline = incidents.firstOrNull { (impact, name) -> impact == worst && name.isNotBlank() }?.second
    return summary(provider, checkedAt, worst, description = headline ?: worst.description,
        incidents = incidents.size, maintenance = payload.strictObjects("activeMaintenances").size)
}

private fun summarizeOpenCode(provider: ServiceStatusProvider, payload: JsonObject, checkedAt: Long): ServiceProviderStatus {
    val pageStatus = payload.text("status")
    require(pageStatus.isNotEmpty())
    var worst = openCodeImpact(pageStatus)
    var affected = 0
    val reasons = mutableSetOf<String>()
    for (component in payload.strictObjects("components")) {
        require(component.text("service").isNotEmpty() && (component["status"] as? JsonPrimitive)?.isString == true)
        val impact = openCodeImpact(component.text("status"))
        worst = maxOf(worst, impact)
        if (impact != Impact.None) {
            affected++
            reasons.add(component.text("reason").lowercase())
        }
    }
    val description = if (reasons == setOf("upstream")) "Upstream provider issues" else worst.description
    return summary(provider, checkedAt, worst, description, affected = affected)
}

private fun openCodeImpact(value: String): Impact = when (value.lowercase()) {
    "operational" -> Impact.None
    "partial_outage" -> Impact.Major
    "major_outage", "full_outage", "outage" -> Impact.Critical
    else -> Impact.Minor
}

private fun summary(
    provider: ServiceStatusProvider, checkedAt: Long, impact: Impact, description: String,
    affected: Int = 0, incidents: Int = 0, maintenance: Int = 0,
) = ServiceProviderStatus(provider.id, provider.label, provider.pageUrl, impact.health, description,
    affected, incidents, maintenance, checkedAt)

private fun JsonObject.text(key: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty().trim()

private fun JsonObject.objects(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

private fun JsonObject.strictObjects(key: String): List<JsonObject> {
    val value = this[key] ?: return emptyList()
    require(value is JsonArray)
    return value.map { it as? JsonObject ?: error("Invalid status entry") }
}

private fun List<JsonObject>.activeExcept(inactive: Set<String>) = filter {
    val state = it.text("status").lowercase()
    state.isNotEmpty() && state !in inactive
}

private val operationalComponents = setOf("operational", "under_maintenance")
private val inactiveIncidents = setOf("resolved", "completed", "postmortem")
private val inactiveMaintenance = setOf("completed", "canceled")
private val statuspageImpacts = mapOf(
    "none" to Impact.None, "minor" to Impact.Minor, "major" to Impact.Major, "critical" to Impact.Critical,
)
private val instatusPageImpacts = buildMap {
    put("up", Impact.None)
    put("hasissues", Impact.Minor)
    put("undermaintenance", Impact.None)
    for (prefix in listOf("all", "some", "one")) {
        put(prefix + "undermaintenance", Impact.None)
        put(prefix + "degradedperformance", Impact.Minor)
        put(prefix + "partialoutage", Impact.Major)
        put(prefix + "minoroutage", Impact.Minor)
        put(prefix + "majoroutage", Impact.Critical)
    }
}
