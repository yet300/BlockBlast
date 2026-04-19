package ge.yet.blokblast.data.repository

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getBooleanStateFlow
import com.russhwolf.settings.coroutines.getIntStateFlow
import com.russhwolf.settings.coroutines.getLongStateFlow
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

    override val bestScore: StateFlow<Long> =
        settings.getLongStateFlow(scope, KEY_BEST_SCORE, defaultValue = 0L)

    override val reviewPromptCount: StateFlow<Int> =
        settings.getIntStateFlow(scope, KEY_REVIEW_PROMPT_COUNT, defaultValue = 0)

    override suspend fun setSoundEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SOUND, enabled)
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_VIBRATION, enabled)
    }

    override suspend fun setDarkTheme(enabled: Boolean) {
        settings.putBoolean(KEY_DARK, enabled)
    }

    override suspend fun setBestScore(score: Long) {
        // Monotonic — never overwrite a higher persisted best with a lower one.
        if (score > settings.getLong(KEY_BEST_SCORE, 0L)) {
            settings.putLong(KEY_BEST_SCORE, score)
        }
    }

    override suspend fun incrementReviewPromptCount() {
        val next = settings.getInt(KEY_REVIEW_PROMPT_COUNT, 0) + 1
        settings.putInt(KEY_REVIEW_PROMPT_COUNT, next)
    }

    private companion object {
        const val KEY_SOUND = "blockblast.sound"
        const val KEY_VIBRATION = "blockblast.vibration"
        const val KEY_DARK = "blockblast.dark_theme"
        const val KEY_BEST_SCORE = "blockblast.best_score"
        const val KEY_REVIEW_PROMPT_COUNT = "blockblast.review_prompt_count"
    }
}
