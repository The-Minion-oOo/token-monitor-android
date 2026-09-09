package io.github.theminionooo.tokenmonitor.domain

enum class ServiceHealth {
    Ok,
    Degraded,
    Outage,
    Unknown,
}

data class ServiceProviderStatus(
    val id: String,
    val label: String,
    val pageUrl: String,
    val health: ServiceHealth,
    val description: String,
    val affectedComponents: Int,
    val incidentCount: Int,
    val maintenanceCount: Int,
    val checkedAt: Long,
)

data class ServiceStatusSnapshot(
    val providers: List<ServiceProviderStatus> = emptyList(),
    val checkedAt: Long = 0L,
)
