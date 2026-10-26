package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.R
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.github.theminionooo.tokenmonitor.data.network.ExchangeRateClient
import io.github.theminionooo.tokenmonitor.data.storage.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val LocalUsageCurrency = compositionLocalOf { UsageCurrency() }
internal val LocalRefreshRates = compositionLocalOf<() -> Unit> { {} }
internal val LocalRateStatus = compositionLocalOf { "" }

@Composable
internal fun displayUsageCost(value: Double, unpricedTokens: Long = 0, compact: Boolean = false) =
    formatUsageCost(value, unpricedTokens, compact, LocalUsageCurrency.current)

@Composable
internal fun CurrencyRateEffect(options: DisplayOptions, onChange: (DisplayOptions) -> Unit, content: @Composable (() -> Unit, String) -> Unit) {
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(options)
    val callback by rememberUpdatedState(onChange)
    val client = remember { ExchangeRateClient() }
    var job by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf("") }
    val refresh: () -> Unit = {
        if (job?.isActive != true) job = scope.launch {
            status = appText(R.string.ui_updating_exchange_rates_9d274, "Updating exchange rates…")
            try {
                val rates = withContext(Dispatchers.IO) { client.fetch() }
                callback(current.copy(exchangeRates = rates.rates, exchangeRateDate = rates.date, exchangeRateFetchedAt = System.currentTimeMillis()))
                status = appText(R.string.ui_exchange_rates_updated_75bcc, "Exchange rates updated")
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { status = appText(R.string.ui_rates_unavailable_the_last_saved_rate_is_retained_64e96, "Rates unavailable; the last saved rate is retained.") }
        }
    }
    val owner = LocalContext.current.findActivity() as? LifecycleOwner
    DisposableEffect(owner, client) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { client.close(); job?.cancel() }
            if (event == Lifecycle.Event.ON_RESUME && current.currencyCode != "USD" && !current.manualCurrencyRate &&
                (current.exchangeRates.isEmpty() || System.currentTimeMillis() - current.exchangeRateFetchedAt >= 86_400_000)) refresh()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer); client.close(); job?.cancel() }
    }
    LaunchedEffect(options.currencyCode, options.manualCurrencyRate) {
        if (options.currencyCode != "USD" && !options.manualCurrencyRate &&
            (options.exchangeRates.isEmpty() || System.currentTimeMillis() - options.exchangeRateFetchedAt >= 86_400_000)) refresh()
    }
    content(refresh, status)
}

@Composable
internal fun CurrencySettings(options: DisplayOptions, onChange: (DisplayOptions) -> Unit) {
    SettingsGroup(appText(R.string.ui_display_currency_fb2a7, "Display currency"), summary = options.currencyCode) {
        Text(appText(R.string.ui_convert_estimated_usage_costs_only_provider_balances_0db89, "Convert estimated usage costs only. Provider balances and recorded payments keep their original currency."), color = Muted, style = MaterialTheme.typography.bodySmall)
        usageCurrencies.chunked(3).forEach { row -> ChoiceGroup(row.map { it to it }, options.currencyCode, { onChange(options.copy(currencyCode = it)) }) }
        if (options.currencyCode != "USD") {
            SettingsToggle(appText(R.string.ui_use_a_manual_exchange_rate_a246d, "Use a manual exchange rate"), appText(R.string.ui_units_of_the_chosen_currency_for_one_usd_ae56b, "Units of the chosen currency for one USD."), options.manualCurrencyRate) { onChange(options.copy(manualCurrencyRate = it)) }
            if (options.manualCurrencyRate) {
                var entered by remember(options.currencyCode) { mutableStateOf(options.manualRates[options.currencyCode]?.toString().orEmpty()) }
                val rate = entered.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
                OutlinedTextField(entered, { entered = it }, label = { Text(appText(R.string.ui_currency_units_per_usd_1dd22, "Currency units per USD")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = entered.isNotBlank() && rate == null)
                TextButton(onClick = { rate?.let { onChange(options.copy(manualRates = options.manualRates + (options.currencyCode to it))) } }, enabled = rate != null) { Text(appText(R.string.ui_save_exchange_rate_1597f, "Save exchange rate")) }
            } else {
                Text(if (options.exchangeRateDate.isBlank()) appText(R.string.ui_no_exchange_rate_yet_usage_is_shown_in_usd_792df, "No exchange rate yet; usage is shown in USD.") else appText(R.string.ui_saved_exchange_rate_date_1_s_dad37, "Saved exchange-rate date: %1\$s", options.exchangeRateDate), color = Muted, style = MaterialTheme.typography.labelSmall)
                Text(appText(R.string.ui_automatic_rates_use_the_same_public_currency_service_37125, "Automatic rates use the same public currency service as desktop, at most daily while the app is visible. Widgets use the saved rate."), color = Muted, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = LocalRefreshRates.current) { Text(appText(R.string.ui_refresh_exchange_rates_19d46, "Refresh exchange rates")) }
                if (LocalRateStatus.current.isNotBlank()) Text(LocalRateStatus.current, color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            if (usageCurrency(options).code == "USD") Text(appText(R.string.ui_usage_is_shown_in_usd_until_a_valid_rate_is_availabl_1a8b2, "Usage is shown in USD until a valid rate is available."), color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
