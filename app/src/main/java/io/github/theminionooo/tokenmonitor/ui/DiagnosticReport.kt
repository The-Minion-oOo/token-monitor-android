package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.BuildConfig
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.network.HubAddressValidator
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshRecord
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshSettings
import io.github.theminionooo.tokenmonitor.data.storage.usageCurrencies

/** An allowlist, not a scrubber: never serialize repository state, URLs, errors or cached payloads. */
internal fun diagnosticReport(state: HubRepositoryState, options: DisplayOptions,
    settings: WidgetRefreshSettings, record: WidgetRefreshRecord, sdk: Int): String {
    val route = when (HubAddressValidator.routeLabel(state.activeUrl ?: state.connectionUrl, state.fallbackUrl)) {
        "Tailscale" -> "tailscale"
        "Home Wi-Fi", "Private network" -> "private-network"
        "Internet (HTTPS)" -> "internet-https"
        else -> "none-or-unclassified"
    }
    val status = when {
        state.secretRejected -> "secret-rejected"
        state.localNetworkPermissionRequired -> "local-network-permission-required"
        state.refreshing -> "refreshing"
        state.streamActive -> "streaming"
        state.message != null -> "error-details-omitted"
        state.hasConnection -> "paired-idle"
        else -> "unpaired"
    }
    return linkedMapOf(
        "report_schema" to "1", "app_version" to BuildConfig.VERSION_NAME,
        "app_version_code" to BuildConfig.VERSION_CODE.toString(), "desktop_baseline" to BuildConfig.UPSTREAM_TAG,
        "android_api" to sdk.toString(), "paired" to state.hasConnection.toString(),
        "route_type" to route, "home_fallback_saved" to (state.fallbackUrl != null).toString(),
        "internet_opt_in" to state.allowPublicHttps.toString(), "private_network_opt_in" to state.allowLocalNetwork.toString(),
        "connection_state" to status, "snapshot_present" to (state.snapshot != null).toString(),
        "snapshot_saved" to (state.snapshot?.fromCache == true).toString(), "snapshot_stale" to (state.snapshot?.stale == true).toString(),
        "widget_live" to state.widgetLiveActive.toString(), "widget_background_minutes" to settings.interval.minutes.toString(),
        "widget_wifi_only" to settings.wifiOnly.toString(), "widget_last_success_ms" to record.successAt.toString(),
        "widget_last_failure_ms" to record.failureAt.toString(), "widget_secret_rejected" to record.secretRejected.toString(),
        "widget_last_response_bytes" to record.receivedBytes.coerceAtLeast(0).toString(), "widget_last_run_completed" to record.completed.toString(),
        "display_currency" to options.currencyCode.takeIf { it in usageCurrencies }.orEmpty(),
        "display_hidden_tools_count" to options.hiddenTools.size.toString(), "display_hidden_quota_items_count" to options.hiddenLimitItems.size.toString(),
    ).entries.joinToString("\n", prefix = "Token Monitor Android diagnostic report\n") { "${it.key}: ${it.value}" }
}
