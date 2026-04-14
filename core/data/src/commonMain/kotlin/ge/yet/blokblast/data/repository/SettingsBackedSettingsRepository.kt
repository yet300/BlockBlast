package ge.yet.blokblast.data.repository

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getBooleanStateFlow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * User-preference storage backed by multiplatform-settings' [ObservableSettings].
 *
 * Uses the `multiplatform-settings-coroutines` extension [getBooleanStateFlow]
 * so the flows stay in sync with the underlying store automatically — no manual
 * mirror required, and external writes (e.g. from a platform Settings screen)
 * propagate to observers too.
 *
 * `internal` — only the [SettingsRepository] interface is exposed through DI.
 */
@OptIn(ExperimentalSettingsApi::class)
@SingleIn(AppScope::class)
@Inject
internal class SettingsBackedSettingsRepository(
    private val settings: ObservableSettings,
    private val scope: CoroutineScope,
) : SettingsRepository {

    override val soundEnabled: StateFlow<Boolean> =
        settings.getBooleanStateFlow(scope, KEY_SOUND, defaultValue = true)

    override val vibrationEnabled: StateFlow<Boolean> =
        settings.getBooleanStateFlow(scope, KEY_VIBRATION, defaultValue = true)

    override val darkTheme: StateFlow<Boolean> =
        settings.getBooleanStateFlow(scope, KEY_DARK, defaultValue = false)

    override suspend fun setSoundEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SOUND, enabled)
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_VIBRATION, enabled)
    }

    override suspend fun setDarkTheme(enabled: Boolean) {
        settings.putBoolean(KEY_DARK, enabled)
    }

    private companion object {
        const val KEY_SOUND = "blockblast.sound"
        const val KEY_VIBRATION = "blockblast.vibration"
        const val KEY_DARK = "blockblast.dark_theme"
    }
}
