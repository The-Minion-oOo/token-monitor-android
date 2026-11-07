package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.domain.ModelAliasSettings
import io.github.theminionooo.tokenmonitor.domain.ModelNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val LocalModelNames = staticCompositionLocalOf { ModelNames() }

/** Session collections can be large. Compose keys compare references only. */
internal class ModelAliasInputs(private val snapshot: HubSnapshot) {
    override fun equals(other: Any?): Boolean = other is ModelAliasInputs &&
        snapshot.stats === other.snapshot.stats && snapshot.history === other.snapshot.history
    override fun hashCode(): Int = 31 * System.identityHashCode(snapshot.stats) + System.identityHashCode(snapshot.history)
}

private data class ResolvedModelNames(val settings: ModelAliasSettings, val names: ModelNames)

@Composable
internal fun rememberModelNames(snapshot: HubSnapshot?, settings: ModelAliasSettings?): ModelNames {
    if (snapshot == null || settings == null || settings.revision != snapshot.stats.modelAliasRevision) return ModelNames()
    val inputs = ModelAliasInputs(snapshot)
    val resolved = produceState<ResolvedModelNames?>(null, inputs, settings) {
        value = withContext(Dispatchers.Default) { ResolvedModelNames(settings, settings.resolver(snapshot)) }
    }.value
    return resolved?.takeIf { it.settings === settings }?.names ?: remember(settings) { ModelNames(settings.aliases) }
}
