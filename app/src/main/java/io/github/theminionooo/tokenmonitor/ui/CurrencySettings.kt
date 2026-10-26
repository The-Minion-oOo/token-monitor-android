package io.github.theminionooo.tokenmonitor.ui

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
            status = "Updating exchange rates…"
            try {
                val rates = withContext(Dispatchers.IO) { client.fetch() }
                callback(current.copy(exchangeRates = rates.rates, exchangeRateDate = rates.date, exchangeRateFetchedAt = System.currentTimeMillis()))
                status = "Exchange rates updated"
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { status = "Rates unavailable; the last saved rate is retained." }
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
    SettingsGroup("Display currency", summary = options.currencyCode) {
        Text("Convert estimated usage costs only. Provider balances and recorded payments keep their original currency.", color = Muted, style = MaterialTheme.typography.bodySmall)
        usageCurrencies.chunked(3).forEach { row -> ChoiceGroup(row.map { it to it }, options.currencyCode, { onChange(options.copy(currencyCode = it)) }) }
        if (options.currencyCode != "USD") {
            SettingsToggle("Use a manual exchange rate", "Units of the chosen currency for one USD.", options.manualCurrencyRate) { onChange(options.copy(manualCurrencyRate = it)) }
            if (options.manualCurrencyRate) {
                var entered by remember(options.currencyCode) { mutableStateOf(options.manualRates[options.currencyCode]?.toString().orEmpty()) }
                val rate = entered.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
                OutlinedTextField(entered, { entered = it }, label = { Text("Currency units per USD") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = entered.isNotBlank() && rate == null)
                TextButton(onClick = { rate?.let { onChange(options.copy(manualRates = options.manualRates + (options.currencyCode to it))) } }, enabled = rate != null) { Text("Save exchange rate") }
            } else {
                Text(if (options.exchangeRateDate.isBlank()) "No exchange rate yet; usage is shown in USD." else "Saved exchange-rate date: ${options.exchangeRateDate}", color = Muted, style = MaterialTheme.typography.labelSmall)
                Text("Automatic rates use the same public currency service as desktop, at most daily while the app is visible. Widgets use the saved rate.", color = Muted, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = LocalRefreshRates.current) { Text("Refresh exchange rates") }
                if (LocalRateStatus.current.isNotBlank()) Text(LocalRateStatus.current, color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            if (usageCurrency(options).code == "USD") Text("Usage is shown in USD until a valid rate is available.", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
