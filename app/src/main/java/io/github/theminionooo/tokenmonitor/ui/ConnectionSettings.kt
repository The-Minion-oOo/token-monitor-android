package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.theminionooo.tokenmonitor.BuildConfig
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.network.HubAddressValidator
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.data.storage.LimitBarMetric
import io.github.theminionooo.tokenmonitor.data.storage.RankingMetric
import io.github.theminionooo.tokenmonitor.data.storage.ReduceMotionMode
import io.github.theminionooo.tokenmonitor.data.storage.TextScale
import io.github.theminionooo.tokenmonitor.data.storage.TokenNumberAnimation
import java.util.Locale

private val LocalOpenSettingsSection = compositionLocalOf<MutableState<String?>?> { null }

@Composable
internal fun ConnectionScreen(
    modifier: Modifier,
    state: HubRepositoryState,
    form: ConnectionFormState,
    displayOptions: DisplayOptions,
    onSaveConnection: (String, String, String, Boolean, Boolean) -> Unit,
    onColorfulToolMarksChange: (Boolean) -> Unit,
    onCompactTokenTotalChange: (Boolean) -> Unit,
    onReduceMotionChange: (ReduceMotionMode) -> Unit,
    onTextScaleChange: (TextScale) -> Unit,
    onThemeCodeChange: (String?) -> Unit,
    onFollowSystemThemeChange: (Boolean) -> Unit,
    onShowLiveIndicatorChange: (Boolean) -> Unit,
    onShowToolIconsChange: (Boolean) -> Unit,
    onRankingMetricChange: (RankingMetric) -> Unit,
    onShowLimitSourceChange: (Boolean) -> Unit,
    onShowAccountEmailsChange: (Boolean) -> Unit,
    onLimitBarMetricChange: (LimitBarMetric) -> Unit,
    onDefaultPeriodChange: (String) -> Unit,
    onViewVisibleChange: (String, Boolean) -> Unit,
    onHomeModuleVisibleChange: (String, Boolean) -> Unit,
    onMoveView: (String, Int) -> Unit,
    onMoveHomeModule: (String, Int) -> Unit,
    onDisconnect: () -> Unit,
    onOpenReleasePage: () -> Unit,
    discovery: HubDiscoveryState,
    onFindHomeHub: () -> Unit,
    onShowSessionTitlesChange: (Boolean) -> Unit = {},
    onRepairHomeAddress: (String) -> Unit = {},
    onAllowLocalNetwork: () -> Unit = {},
    onTokenNumberAnimationChange: (TokenNumberAnimation) -> Unit = {},
    onHomeListCountChange: (String, Int) -> Unit = { _, _ -> },
    onDisablePublicHttps: () -> Unit = {},
) {
    val hasConnection = state.hasConnection
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var hubUrl by rememberSaveable(state.connectionUrl) { mutableStateOf(state.connectionUrl.orEmpty()) }
    var fallbackUrl by rememberSaveable(state.fallbackUrl) { mutableStateOf(state.fallbackUrl.orEmpty()) }
    var secret by remember { mutableStateOf("") }
    var allowLocalNetwork by rememberSaveable(state.allowLocalNetwork) { mutableStateOf(state.allowLocalNetwork) }
    var allowPublicHttps by rememberSaveable(state.allowPublicHttps) { mutableStateOf(state.allowPublicHttps) }
    var confirmInternetOff by rememberSaveable(state.connectionUrl, state.allowPublicHttps, state.hasConnection) { mutableStateOf(false) }
    if (confirmInternetOff) {
        InternetAccessOffDialog(
            onDismiss = { confirmInternetOff = false },
            onConfirm = {
                confirmInternetOff = false
                allowPublicHttps = false
                onDisablePublicHttps()
            },
        )
    }
    // A discovery result fills the home address once. Recreating the screen must not put an old result over what was typed since.
    var appliedDiscovery by rememberSaveable { mutableStateOf(discovery.found) }
    LaunchedEffect(discovery.found) {
        val found = discovery.found
        if (found != appliedDiscovery) {
            appliedDiscovery = found
            found?.let { fallbackUrl = it }
        }
    }
    val fields: @Composable ColumnScope.(String) -> Unit = { saveLabel ->
        if (state.localNetworkPermissionRequired || form.result == io.github.theminionooo.tokenmonitor.data.network.LocalNetworkAccess.deniedMessage) {
            Text(appText(R.string.ui_android_needs_your_permission_to_reach_the_home_wi_f_66823, "Android needs your permission to reach the home Wi-Fi Hub. Tailscale can still be used when available."), color = Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onAllowLocalNetwork, modifier = Modifier.fillMaxWidth()) { Text(appText(R.string.ui_allow_home_wi_fi_access_411c0, "ALLOW HOME WI-FI ACCESS")) }
            val permissionContext = androidx.compose.ui.platform.LocalContext.current
            TextButton(onClick = {
                permissionContext.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${permissionContext.packageName}")))
            }) { Text(appText(R.string.ui_open_android_permissions_49652, "Open Android permissions")) }
        }
        ConnectionFields(
            hubUrl = hubUrl,
            onHubUrlChange = { hubUrl = it },
            fallbackUrl = fallbackUrl,
            onFallbackUrlChange = { fallbackUrl = it },
            secret = secret,
            onSecretChange = { secret = it },
            allowLocalNetwork = allowLocalNetwork,
            onAllowLocalNetworkChange = { allowLocalNetwork = it },
            allowPublicHttps = allowPublicHttps,
            onAllowPublicHttpsChange = {
                if (!it && state.hasConnection && state.allowPublicHttps &&
                    HubAddressValidator.routeLabel(state.connectionUrl) == "Internet (HTTPS)") {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    confirmInternetOff = true
                } else {
                    allowPublicHttps = it
                    if (!it && state.allowPublicHttps) onDisablePublicHttps()
                }
            },
            saving = form.saving,
            saveLabel = saveLabel,
            result = form.result,
            resultSuccessful = form.successful,
            onSave = { onSaveConnection(hubUrl, fallbackUrl, secret, allowLocalNetwork, allowPublicHttps) },
            discovery = discovery,
            onFindHomeHub = onFindHomeHub,
            canRepairHomeAddress = hasConnection && fallbackUrl.isNotBlank() && fallbackUrl != state.fallbackUrl,
            onRepairHomeAddress = { onRepairHomeAddress(fallbackUrl) },
        )
        DiagnosticActions(state, displayOptions)
        if (!hasConnection) LanguageSettings()
    }
    if (!hasConnection) {
        WelcomeSetup(modifier = modifier, fields = fields, onOpenReleasePage = onOpenReleasePage)
        return
    }
    val openSection = rememberSaveable { mutableStateOf<String?>(null) }
    CompositionLocalProvider(LocalOpenSettingsSection provides openSection) {
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val route = when {
                state.fallbackUrl != null && state.activeUrl == state.fallbackUrl -> "Home Wi-Fi"
                io.github.theminionooo.tokenmonitor.data.network.HubAddressValidator.routeLabel(state.connectionUrl) == "Internet (HTTPS)" -> "Internet (HTTPS)"
                else -> state.connectionUrl.orEmpty().removePrefix("http://").removePrefix("https://").substringBefore(':')
            }
            SettingsGroup(appText(R.string.ui_connection_6512e, "Connection"), summary = listOfNotNull(route, appText(R.string.ui_home_fallback_ba90d, "home fallback").takeIf { state.fallbackUrl != null && route != "Home Wi-Fi" }).joinToString(" · ")) {
                Text(appText(R.string.ui_this_phone_only_reads_your_hub_it_cannot_control_the_654a0, "This phone only reads your Hub. It cannot control the desktop or access its files."), color = Muted, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                Text(appText(R.string.ui_repair_the_home_address_with_the_saved_pairing_or_en_07412, "Repair the home address with the saved pairing, or enter the secret again to replace the connection."), color = Accent, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                fields(appText(R.string.ui_check_and_save_connection_e118a, "CHECK AND SAVE CONNECTION"))
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text(appText(R.string.ui_disconnect_this_phone_f8a16, "DISCONNECT THIS PHONE")) }
            }
            SettingsGroup(appText(R.string.ui_pair_with_desktop_6759b, "Pair with desktop"), summary = appText(R.string.ui_3_steps_e5516, "3 steps")) {
                Text(appText(R.string.ui_1_install_tailscale_on_the_desktop_and_phone_then_us_9aa66, "1. Install Tailscale on the desktop and phone, then use the same tailnet."), color = Ink, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                Text(appText(R.string.ui_2_in_the_desktop_app_open_settings_multi_device_sync_69c49, "2. In the desktop app, open Settings → Multi-device Sync and choose Host Hub."), color = Ink, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                Text(appText(R.string.ui_3_type_the_address_that_starts_with_100_and_the_shar_009a9, "3. Type the address that starts with 100. and the shared secret from the desktop app into the fields above. Just the numbers are enough."), color = Ink, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                Text(appText(R.string.language_no_pairing_qr, "The v%1\$s desktop app does not generate a pairing QR code.", BuildConfig.UPSTREAM_VERSION), color = Muted, style = MaterialTheme.typography.labelSmall, lineHeight = 16.sp)
            }
            val currentTheme = displayOptions.themeCode?.let(InterfaceTheme::fromCode) ?: InterfaceTheme.Default
            val themeId = InterfaceTheme.idOf(currentTheme)
            var themeCodeInput by rememberSaveable(currentTheme.code) { mutableStateOf(currentTheme.code) }
            var themeCodeInvalid by remember { mutableStateOf(false) }
            SettingsGroup(appText(R.string.ui_appearance_41def, "Appearance"), summary = "${if (displayOptions.followSystemTheme) "Follows phone" else themeId.replaceFirstChar { it.titlecase(Locale.US) }} · ${displayLabel(displayOptions.textScale.name)}") {
                Text(appText(R.string.ui_interface_theme_f47f8, "Interface theme"), color = Ink, style = MaterialTheme.typography.bodyMedium)
                ChoiceGroup(
                    options = InterfaceTheme.presets.keys.map { it.uppercase(Locale.US) to it },
                    selected = themeId,
                    onSelect = { id -> onThemeCodeChange(if (id == "default") null else InterfaceTheme.presets[id]?.code) },
                )
                OutlinedTextField(
                    value = themeCodeInput,
                    onValueChange = {
                        themeCodeInput = it
                        themeCodeInvalid = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(appText(R.string.ui_theme_code_72bce, "Theme code")) },
                    supportingText = { Text(if (themeCodeInvalid) appText(R.string.ui_that_is_not_a_valid_tm1_or_tm2_theme_code_9e0f1, "That is not a valid TM1 or TM2 theme code.") else appText(R.string.ui_paste_a_code_from_the_desktop_s_appearance_settings_48759, "Paste a code from the desktop's Appearance settings. TM2 also carries its chart color.")) },
                    isError = themeCodeInvalid,
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                val parsed = InterfaceTheme.fromCode(themeCodeInput)
                                themeCodeInvalid = parsed == null
                                if (parsed != null) onThemeCodeChange(if (parsed == InterfaceTheme.Default) null else parsed.code)
                            },
                            modifier = Modifier.padding(end = 4.dp),
                        ) { Text(appText(R.string.ui_apply_4433c, "APPLY"), color = Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
                    },
                    singleLine = true,
                )
                SettingsToggle(appText(R.string.ui_follow_phone_light_and_dark_9547b, "Follow phone light and dark"), appText(R.string.ui_porcelain_while_the_phone_is_in_light_mode_1_s_at_ni_4330f, "Porcelain while the phone is in light mode, %1\$s at night. Widgets follow at their next update.", if (currentTheme.isLight) "Default" else themeId.replaceFirstChar { it.titlecase(Locale.US) }), displayOptions.followSystemTheme, onFollowSystemThemeChange)
                SettingsToggle(appText(R.string.ui_live_indicator_0ff7b, "Live indicator"), appText(R.string.ui_show_the_small_dot_beside_the_while_the_stream_is_li_64bff, "Show the small dot beside the Σ while the stream is live."), displayOptions.showLiveIndicator, onShowLiveIndicatorChange)
                SettingsToggle(appText(R.string.ui_tool_icons_eeb37, "Tool icons"), appText(R.string.ui_show_provider_marks_beside_tools_and_models_instead_ffa03, "Show provider marks beside tools and models instead of plain dots."), displayOptions.showToolIcons, onShowToolIconsChange)
                SettingsToggle(appText(R.string.ui_colorful_tool_marks_9e679, "Colorful tool marks"), appText(R.string.ui_use_original_provider_colors_instead_of_monochrome_m_ec3aa, "Use original provider colors instead of monochrome marks."), displayOptions.colorfulToolMarks, onColorfulToolMarksChange)
                SettingsToggle(appText(R.string.ui_compact_token_total_11da1, "Compact token total"), appText(R.string.ui_use_abbreviated_totals_such_as_214_9m_in_the_main_he_e4b29, "Use abbreviated totals such as 214.9M in the main header."), displayOptions.compactTokenTotal, onCompactTokenTotalChange)
                Text(appText(R.string.ui_text_size_3cc6e, "Text size"), color = Ink, style = MaterialTheme.typography.bodyMedium)
                ChoiceGroup(
                    options = TextScale.entries.map { displayLabel(it.name).uppercase(AppLanguage.locale()) to it.name },
                    selected = displayOptions.textScale.name,
                    onSelect = { selected -> TextScale.entries.firstOrNull { it.name == selected }?.let(onTextScaleChange) },
                )
                Text(appText(R.string.ui_compact_matches_the_desktop_widget_exactly_comfortab_7f1b9, "Compact matches the desktop widget exactly. Comfortable and Large enlarge every label for reading at arm's length."), color = Muted, style = MaterialTheme.typography.labelSmall)
                Text(appText(R.string.ui_reduce_motion_25a5a, "Reduce motion"), color = Ink, style = MaterialTheme.typography.bodyMedium)
                ChoiceGroup(
                    options = ReduceMotionMode.entries.map { displayLabel(it.name).uppercase(AppLanguage.locale()) to it.name },
                    selected = displayOptions.reduceMotion.name,
                    onSelect = { selected -> ReduceMotionMode.entries.firstOrNull { it.name == selected }?.let(onReduceMotionChange) },
                )
                Text(appText(R.string.ui_system_follows_android_on_minimizes_motion_off_keeps_bf08e, "System follows Android. On minimizes motion; Off keeps the app's interaction animations."), color = Muted, style = MaterialTheme.typography.labelSmall)
                Text(appText(R.string.ui_token_number_animation_fb9a8, "Token number animation"), color = Ink, style = MaterialTheme.typography.bodyMedium)
                ChoiceGroup(
                    options = listOf(appText(R.string.ui_digit_roll_dde95, "DIGIT ROLL") to TokenNumberAnimation.Roll.name, appText(R.string.ui_count_up_b580c, "COUNT UP") to TokenNumberAnimation.Count.name),
                    selected = displayOptions.tokenNumberAnimation.name,
                    onSelect = { selected -> TokenNumberAnimation.entries.firstOrNull { it.name == selected }?.let(onTokenNumberAnimationChange) },
                )
                Text(appText(R.string.ui_digit_roll_keeps_the_unchanged_leading_digits_and_ro_1d13c, "Digit roll keeps the unchanged leading digits and rolls the rest, like the desktop. Count up counts to the new total."), color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            SettingsGroup(appText(R.string.ui_main_dashboard_5eaea, "Main dashboard"), summary = appText(R.string.ui_1_s_2_s_views_3_s_8ed52, "%1\$s/%2\$s views · %3\$s", displayOptions.visibleViews.size, DisplayOptions.defaultViews.size, DashboardPeriod.entries.firstOrNull { it.name == displayOptions.defaultPeriod }?.let(::periodRangeLabel) ?: "Day")) {
                Text(appText(R.string.ui_default_usage_range_0d7b0, "Default usage range"), color = Ink, style = MaterialTheme.typography.bodyMedium)
                DashboardPeriod.entries.chunked(3).forEach { row ->
                    ChoiceGroup(
                        options = row.map { periodRangeLabel(it).uppercase(Locale.US) to it.name },
                        selected = displayOptions.defaultPeriod,
                        onSelect = onDefaultPeriodChange,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(appText(R.string.ui_views_24be6, "Views"), color = Ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                val orderedViews = displayOptions.visibleViews + DisplayOptions.defaultViews.filterNot { it in displayOptions.visibleViews }
                orderedViews.forEach { view ->
                    SettingsOrderRow(
                        label = displayLabel(view),
                        checked = view in displayOptions.visibleViews,
                        allowDisable = view != "Home",
                        onCheckedChange = { onViewVisibleChange(view, it) },
                        onMove = { onMoveView(view, it) },
                    )
                }
                Text(appText(R.string.ui_home_modules_d881e, "Home modules"), color = Ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                val orderedModules = displayOptions.visibleHomeModules + DisplayOptions.defaultHomeModules.filterNot { it in displayOptions.visibleHomeModules }
                orderedModules.forEach { module ->
                    SettingsOrderRow(
                        label = displayLabel(module),
                        checked = module in displayOptions.visibleHomeModules,
                        onCheckedChange = { onHomeModuleVisibleChange(module, it) },
                        onMove = { onMoveHomeModule(module, it) },
                    )
                    if (module in DisplayOptions.defaultHomeListCounts && module in displayOptions.visibleHomeModules) {
                        HomeListCountControl(module, displayOptions.homeListCount(module)) { onHomeListCountChange(module, it) }
                    }
                }
            }
            SettingsGroup(appText(R.string.ui_usage_presentation_bd842, "Usage presentation"), summary = "${displayLabel(displayOptions.rankingMetric.name)} · ${displayLabel(displayOptions.limitBarMetric.name)}") {
                Text(appText(R.string.ui_model_and_tool_ranking_33668, "Model and tool ranking"), color = Ink, style = MaterialTheme.typography.bodyMedium)
                ChoiceGroup(
                    options = listOf(appText(R.string.ui_tokens_00341, "TOKENS") to RankingMetric.Tokens.name, appText(R.string.ui_cost_75fde, "COST") to RankingMetric.Cost.name),
                    selected = displayOptions.rankingMetric.name,
                    onSelect = { selected -> RankingMetric.entries.firstOrNull { it.name == selected }?.let(onRankingMetricChange) },
                )
                SettingsToggle(appText(R.string.ui_show_limit_source_38d98, "Show limit source"), appText(R.string.ui_display_which_hub_device_supplied_each_provider_limi_c9d43, "Display which Hub device supplied each provider limit."), displayOptions.showLimitSource, onShowLimitSourceChange)
                SettingsToggle(appText(R.string.ui_show_account_emails_5b235, "Show account emails"), appText(R.string.ui_useful_only_when_provider_names_are_not_enough_to_di_28cae, "Useful only when provider names are not enough to distinguish accounts."), displayOptions.showAccountEmails, onShowAccountEmailsChange)
                SettingsToggle(appText(R.string.ui_show_session_titles_440ff, "Show session titles"), appText(R.string.ui_hide_reported_titles_in_home_and_sessions_this_displ_5db83, "Hide reported titles in Home and Sessions. This display choice does not remove titles from the local snapshot cache."), displayOptions.showSessionTitles, onShowSessionTitlesChange)
                Text(appText(R.string.ui_limit_bars_show_25fda, "Limit bars show"), color = Ink, style = MaterialTheme.typography.bodyMedium)
                ChoiceGroup(
                    options = listOf(appText(R.string.ui_remaining_03e91, "REMAINING") to LimitBarMetric.Remaining.name, appText(R.string.ui_used_1f40c, "USED") to LimitBarMetric.Used.name),
                    selected = displayOptions.limitBarMetric.name,
                    onSelect = { selected -> LimitBarMetric.entries.firstOrNull { it.name == selected }?.let(onLimitBarMetricChange) },
                )
            }
            state.snapshot?.let { snapshot ->
                val delivery = when {
                    state.streamActive -> appText(R.string.ui_stream_live_cc4e6, "Stream live")
                    state.widgetLiveActive -> appText(R.string.ui_widget_live_bbe50, "Widget live")
                    else -> appText(R.string.ui_snapshot_b08ae, "Snapshot")
                }
                SettingsGroup(appText(R.string.ui_hub_status_5117a, "Hub status"), summary = delivery) {
                    StatusLine(appText(R.string.ui_connection_6512e, "Connection"), delivery, if (state.streamActive || state.widgetLiveActive) Success else Muted)
                    StatusLine(appText(R.string.ui_route_49995, "Route"), io.github.theminionooo.tokenmonitor.data.network.HubAddressValidator.routeLabel(state.activeUrl ?: state.connectionUrl, state.fallbackUrl))
                    StatusLine(appText(R.string.ui_hub_build_3bb17, "Hub build"), formatHubBuild(snapshot.health.hubBuild))
                    StatusLine(appText(R.string.ui_runtime_c4740, "Runtime"), snapshot.health.runtime.ifBlank { appText(R.string.ui_not_reported_98e85, "Not reported") })
                    StatusLine(appText(R.string.ui_collector_role_dbeda, "Collector role"), snapshot.health.role.ifBlank { appText(R.string.ui_not_reported_98e85, "Not reported") })
                    StatusLine("Devices", snapshot.health.deviceCount.toString())
                    StatusLine(appText(R.string.ui_last_read_c9ec8, "Last read"), formatRelativeAge(snapshot.capturedAt, LocalNow.current).ifBlank { appText(R.string.ui_unknown_bc781, "Unknown") })
                    StatusLine(appText(R.string.ui_phone_data_b89f2, "Phone data"), if (snapshot.fromCache || snapshot.stale) appText(R.string.ui_saved_snapshot_c3933, "Saved snapshot") else appText(R.string.ui_current_4fc0e, "Current"))
                }
            }
            SettingsGroup(appText(R.string.ui_home_screen_widget_c1c9a, "Home-screen widget"), summary = appText(R.string.ui_live_controls_resizable_0fae1, "Live controls · resizable")) {
                WidgetBackgroundSettings()
                val widgetContext = androidx.compose.ui.platform.LocalContext.current
                val widgets = android.appwidget.AppWidgetManager.getInstance(widgetContext)
                Text(appText(R.string.ui_a_widget_in_your_app_theme_with_the_full_token_count_b19ca, "A widget in your app theme with the full token count, tool marks, quota windows, and a seven-day chart as you make it taller. Reduce Motion also applies to the widget."), color = Muted, style = MaterialTheme.typography.bodySmall)
                Text(appText(R.string.ui_tap_refresh_for_one_update_or_live_for_30_second_hub_ed285, "Tap Refresh for one update, or Live for 30-second Hub updates for up to an hour while the app is closed. Tap Live again or Stop in the notification to finish. Android may delay updates while the phone sleeps. Figures and the last update time are visible on your home screen."), color = Muted, style = MaterialTheme.typography.bodySmall)
                if (widgets.isRequestPinAppWidgetSupported) {
                    Button(onClick = {
                        widgets.requestPinAppWidget(android.content.ComponentName(widgetContext, io.github.theminionooo.tokenmonitor.widget.UsageWidgetProvider::class.java), null, null)
                    }) { Text(appText(R.string.ui_add_widget_7d2be, "ADD WIDGET")) }
                } else {
                    Text(appText(R.string.ui_long_press_your_home_screen_choose_widgets_then_toke_763ce, "Long-press your home screen, choose Widgets, then Token Monitor."), color = Ink, style = MaterialTheme.typography.bodySmall)
                }
            }
            LanguageSettings()
            DisplayItemSettings(state.snapshot, displayOptions, LocalDisplayChange.current)
            CurrencySettings(displayOptions, LocalDisplayChange.current)
            LiveRateSettings(displayOptions, LocalDisplayChange.current)
            SettingsGroup("Compatibility", summary = "Desktop v${BuildConfig.UPSTREAM_VERSION}") {
                StatusLine("Android app", BuildConfig.VERSION_NAME)
                StatusLine("Desktop baseline", "Token Monitor v${BuildConfig.UPSTREAM_VERSION}")
                Text(appText(R.string.ui_protocol_changes_are_reviewed_against_versioned_fixt_b6c04, "Protocol changes are reviewed against versioned fixtures before this baseline moves forward."), color = Muted, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
            }
            SettingsGroup("App updates", summary = "${BuildConfig.VERSION_NAME} r${BuildConfig.VERSION_CODE % 1000}") {
                AppUpdatesPanel(onOpenReleasePage)
            }
            Text(appText(R.string.ui_the_dashboard_streams_immediately_while_visible_widg_cb815, "The dashboard streams immediately while visible. Widget Live uses a lighter 30-second refresh and stops after one hour."), color = Muted, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun InternetAccessOffDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        Surface(color = Shell, contentColor = Ink, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, StrongLine)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(appText(R.string.ui_turn_off_internet_access_545ce, "Turn off internet access?"), color = Ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                HorizontalDivider(color = Line)
                Text(appText(R.string.ui_this_phone_will_stop_connecting_to_your_internet_hub_01019, "This phone will stop connecting to your internet Hub. To turn it back on, you'll need to enter the Hub secret again."),
                    color = Ink, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(color = Line)
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.focusProperties { canFocus = true }.focusRequester(cancelFocus)) {
                        Text(appText(R.string.ui_cancel_77dfd, "Cancel"), color = Accent, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = onConfirm) {
                        Text(appText(R.string.ui_turn_off_8807c, "Turn off"), color = Accent, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        LaunchedEffect(windowFocused) { if (windowFocused) cancelFocus.requestFocus() }
    }
}

@Composable
private fun ColumnScope.ConnectionFields(
    hubUrl: String,
    onHubUrlChange: (String) -> Unit,
    fallbackUrl: String,
    onFallbackUrlChange: (String) -> Unit,
    secret: String,
    onSecretChange: (String) -> Unit,
    allowLocalNetwork: Boolean,
    onAllowLocalNetworkChange: (Boolean) -> Unit,
    allowPublicHttps: Boolean,
    onAllowPublicHttpsChange: (Boolean) -> Unit,
    saving: Boolean,
    saveLabel: String,
    result: String?,
    resultSuccessful: Boolean,
    onSave: () -> Unit,
    discovery: HubDiscoveryState,
    onFindHomeHub: () -> Unit,
    canRepairHomeAddress: Boolean,
    onRepairHomeAddress: () -> Unit,
) {
    OutlinedTextField(
        value = hubUrl,
        onValueChange = onHubUrlChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(if (allowPublicHttps || allowLocalNetwork) appText(R.string.ui_hub_address_a46d6, "Hub address") else appText(R.string.ui_tailscale_address_97355, "Tailscale address")) },
        placeholder = { Text(if (allowPublicHttps) "hub.example.com" else "100.x.x.x") },
        supportingText = { Text(if (allowPublicHttps) appText(R.string.ui_use_a_public_dns_name_such_as_hub_example_com_https_9d2ef, "Use a public DNS name such as hub.example.com. HTTPS is required. Tailscale remains recommended.") else appText(R.string.ui_tailscale_is_recommended_use_its_100_x_address_or_ts_b1946, "Tailscale is recommended. Use its 100.x address or .ts.net name. Internet Hubs need the separate HTTPS switch below.")) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    OutlinedTextField(
        value = fallbackUrl,
        onValueChange = onFallbackUrlChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(appText(R.string.ui_home_wi_fi_address_optional_4305a, "Home Wi-Fi address (optional)")) },
        placeholder = { Text("192.168.x.x") },
        supportingText = { Text(discovery.message ?: appText(R.string.ui_used_when_tailscale_does_not_answer_such_as_at_home_4e5cf, "Used when Tailscale does not answer, such as at home with Tailscale off. Tap Find while on your home Wi-Fi to fill it in.")) },
        trailingIcon = {
            if (discovery.searching) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp).size(18.dp), strokeWidth = 2.dp, color = Accent)
            } else {
                TextButton(onClick = onFindHomeHub, modifier = Modifier.padding(end = 4.dp)) { Text(appText(R.string.ui_find_29e8a, "FIND"), color = Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    OutlinedTextField(value = secret, onValueChange = onSecretChange, modifier = Modifier.fillMaxWidth(), label = { Text(appText(R.string.ui_hub_secret_10c42, "Hub secret")) }, supportingText = { Text(appText(R.string.ui_stored_with_an_android_keystore_key_and_never_shown_145ad, "Stored with an Android Keystore key and never shown after saving.")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
    if (canRepairHomeAddress) {
        OutlinedButton(onClick = onRepairHomeAddress, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (saving) appText(R.string.ui_checking_home_address_02f01, "CHECKING HOME ADDRESS…") else appText(R.string.ui_test_and_save_home_address_d6e54, "TEST AND SAVE HOME ADDRESS"))
        }
        Text(appText(R.string.ui_uses_the_saved_hub_secret_to_verify_only_this_home_a_e6220, "Uses the saved Hub secret to verify only this home address. Other edited fields are not saved."), color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(appText(R.string.ui_use_wi_fi_as_the_main_hub_address_0e331, "Use Wi-Fi as the main Hub address"), color = Ink, style = MaterialTheme.typography.bodyMedium)
            Text(appText(R.string.ui_allows_a_private_address_in_the_main_field_a_saved_h_04dbb, "Allows a private address in the main field. A saved home fallback is enabled separately by entering its address. Internet access uses its own switch below."), color = Muted, style = MaterialTheme.typography.bodySmall, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = allowLocalNetwork, onCheckedChange = onAllowLocalNetworkChange)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(appText(R.string.ui_allow_an_internet_hub_over_https_fcbdd, "Allow an internet Hub over HTTPS"), color = Ink, style = MaterialTheme.typography.bodyMedium)
            Text(appText(R.string.ui_off_by_default_connect_to_an_existing_https_hub_with_c4778, "Off by default. Connect to an existing HTTPS Hub with a public DNS name. This does not expose your computer. Tailscale is recommended."), color = Muted, style = MaterialTheme.typography.bodySmall, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = allowPublicHttps, onCheckedChange = onAllowPublicHttpsChange, enabled = !saving,
            modifier = Modifier.semantics { contentDescription = appText(R.string.ui_allow_an_internet_hub_over_https_fcbdd, "Allow an internet Hub over HTTPS") })
    }
    if (allowPublicHttps) {
        Text(appText(R.string.ui_the_hub_secret_can_also_change_data_on_the_hub_use_a_76ae1, "The Hub secret can also change data on the Hub. Use a long random secret. If this phone is lost, change the secret on the Hub and on every desktop."), color = Accent, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
    }
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = !saving) { Text(if (saving) appText(R.string.ui_checking_hub_0fee3, "CHECKING HUB…") else saveLabel) }
    result?.let { StatusMessage(it, stale = !resultSuccessful) }
}

@Composable
private fun WelcomeSetup(modifier: Modifier, fields: @Composable ColumnScope.(String) -> Unit, onOpenReleasePage: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(color = Overlay, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, StrongLine), modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("Σ", color = Accent, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Token Monitor", color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(appText(R.string.ui_your_desktop_dashboard_live_on_your_phone_2b760, "Your desktop dashboard, live on your phone."), color = Muted, style = MaterialTheme.typography.bodyMedium, lineHeight = 18.sp)
        }
        Text(appText(R.string.ui_pair_with_your_desktop_e4ea0, "PAIR WITH YOUR DESKTOP"), color = Ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        WelcomeStep(1, appText(R.string.ui_install_tailscale_on_the_desktop_and_this_phone_11bc0, "Install Tailscale on the desktop and this phone"), appText(R.string.ui_sign_both_into_the_same_tailnet_personal_use_is_free_27772, "Sign both into the same tailnet. Personal use is free."))
        WelcomeStep(2, appText(R.string.ui_host_the_hub_on_the_desktop_23e47, "Host the Hub on the desktop"), appText(R.string.ui_token_monitor_settings_multi_device_sync_host_hub_708d1, "Token Monitor → Settings → Multi-device Sync → Host Hub."))
        WelcomeStep(3, appText(R.string.ui_type_the_address_that_starts_with_100_a2c12, "Type the address that starts with 100."), appText(R.string.ui_just_the_numbers_from_the_desktop_s_list_tap_find_fo_9ff84, "Just the numbers from the desktop's list. Tap Find for the home address, paste the secret, and connect."))
        Surface(color = Recessed.copy(alpha = 0.76f), border = BorderStroke(1.dp, Line), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { fields(appText(R.string.ui_connect_6e288, "CONNECT")) }
        }
        Text(appText(R.string.ui_connect_privately_with_tailscale_recommended_or_enab_6df72, "Connect privately with Tailscale (recommended), or enable an existing internet Hub over HTTPS. App update checks contact GitHub."), color = Muted, style = MaterialTheme.typography.labelSmall, lineHeight = 15.sp)
        SettingsGroup("App updates", summary = "${BuildConfig.VERSION_NAME} r${BuildConfig.VERSION_CODE % 1000}") {
            AppUpdatesPanel(onOpenReleasePage)
        }
    }
}

@Composable
private fun WelcomeStep(number: Int, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = Accent.copy(alpha = 0.12f), shape = MaterialTheme.shapes.extraLarge, border = BorderStroke(1.dp, Accent.copy(alpha = 0.4f)), modifier = Modifier.size(26.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(number.toString(), color = Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(title, color = Ink, style = MaterialTheme.typography.bodyMedium, lineHeight = 17.sp)
            Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
        }
    }
}

@Composable
internal fun SettingsGroup(
    title: String,
    summary: String? = null,
    collapsible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val openSection = LocalOpenSettingsSection.current
    var standaloneExpanded by rememberSaveable(title, collapsible) { mutableStateOf(!collapsible) }
    val expanded = if (collapsible && openSection != null) openSection.value == title else standaloneExpanded
    val toggle: () -> Unit = {
        if (collapsible && openSection != null) openSection.value = if (expanded) null else title else standaloneExpanded = !standaloneExpanded
    }
    val motionEnabled = LocalInteractionMotion.current
    Surface(color = Recessed.copy(alpha = 0.76f), border = BorderStroke(1.dp, Line), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (collapsible) Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 2.dp) else Modifier.fillMaxWidth(),
            ) {
                Text(title.uppercase(Locale.US), color = Ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                if (summary != null && !expanded) {
                    Text(summary, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (collapsible) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (expanded) appText(R.string.ui_collapse_1_s_369a4, "Collapse %1\$s", title) else appText(R.string.ui_expand_1_s_642c8, "Expand %1\$s", title),
                        tint = Muted,
                        modifier = Modifier.size(16.dp).rotate(rememberChevronRotation(expanded)),
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(if (motionEnabled) 160 else 0)) + expandVertically(tween(if (motionEnabled) 240 else 0, easing = DesktopEaseOut)),
                exit = fadeOut(tween(if (motionEnabled) 100 else 0)) + shrinkVertically(tween(if (motionEnabled) 180 else 0, easing = DesktopEaseOut)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
            }
        }
    }
}

@Composable
internal fun SettingsToggle(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = Ink, style = MaterialTheme.typography.bodyMedium)
            Text(description, color = Muted, style = MaterialTheme.typography.bodySmall, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun SettingsOrderRow(
    label: String,
    checked: Boolean,
    allowDisable: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (checked) Ink else Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        IconButton(onClick = { onMove(-1) }, enabled = checked, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = appText(R.string.ui_move_1_s_up_a55bd, "Move %1\$s up", label), tint = if (checked) Muted else Muted.copy(alpha = 0.35f), modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = { onMove(1) }, enabled = checked, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = appText(R.string.ui_move_1_s_down_53326, "Move %1\$s down", label), tint = if (checked) Muted else Muted.copy(alpha = 0.35f), modifier = Modifier.size(16.dp))
        }
        if (allowDisable) {
            Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.size(width = 50.dp, height = 32.dp))
        } else {
            Text(appText(R.string.ui_always_ae0ec, "ALWAYS"), color = Muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.width(50.dp))
        }
    }
}
