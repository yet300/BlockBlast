package ge.yet.blokblast.data.repository

import com.app.common.AppDispatchers
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * User-preference storage backed by multiplatform-settings' [ObservableSettings].
 *
 * Uses the `multiplatform-settings-coroutines` extension [getBooleanStateFlow]
 * so the flows stay in sync with the underlying store automatically — no manual
 * mirror required, and external writes (e.g. from a platform Settings screen)
 * propagate to observers too.
 *
 * All writes are funnelled through [dispatchers.io] + [mutex]: the IO dispatcher
 * keeps disk-backed puts off Main, and the mutex serialises the read-modify-write
 * pairs in [incrementReviewPromptCount] / [setBestScore] so concurrent callers
 * never lose an update.
 *
 * `internal` — only the [SettingsRepository] interface is exposed through DI.
 */
@OptIn(ExperimentalSettingsApi::class)
@SingleIn(AppScope::class)
@Inject
internal class SettingsBackedSettingsRepository(
    private val settings: ObservableSettings,
    private val scope: CoroutineScope,
    private val dispatchers: AppDispatchers,
) : SettingsRepository {

    private val writeMutex = Mutex()

    init {
        // 1.5.0 migration: prior versions had a single KEY_SOUND_LEGACY flag
        // that gated both music and SFX. Seed each new key from it once so an
        // existing "muted" user stays muted on the first launch after upgrade.
        migrateLegacySoundFlag()
    }

    override val musicEnabled: StateFlow<Boolean> =
        settings.getBooleanStateFlow(scope, KEY_MUSIC, defaultValue = true)

    override val sfxEnabled: StateFlow<Boolean> =
        settings.getBooleanStateFlow(scope, KEY_SFX, defaultValue = true)

    override val vibrationEnabled: StateFlow<Boolean> =
        settings.getBooleanStateFlow(scope, KEY_VIBRATION, defaultValue = true)

    override val darkTheme: StateFlow<Boolean> =
        settings.getBooleanStateFlow(scope, KEY_DARK, defaultValue = false)

    override val bestScore: StateFlow<Long> =
        settings.getLongStateFlow(scope, KEY_BEST_SCORE, defaultValue = 0L)

    override val reviewPromptCount: StateFlow<Int> =
        settings.getIntStateFlow(scope, KEY_REVIEW_PROMPT_COUNT, defaultValue = 0)

    override val tutorialSeen: StateFlow<Boolean> =
        settings.getBooleanStateFlow(scope, KEY_TUTORIAL_SEEN, defaultValue = false)

    override suspend fun setMusicEnabled(enabled: Boolean) = withContext(dispatchers.io) {
        settings.putBoolean(KEY_MUSIC, enabled)
    }

    override suspend fun setSfxEnabled(enabled: Boolean) = withContext(dispatchers.io) {
        settings.putBoolean(KEY_SFX, enabled)
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) = withContext(dispatchers.io) {
        settings.putBoolean(KEY_VIBRATION, enabled)
    }

    override suspend fun setDarkTheme(enabled: Boolean) = withContext(dispatchers.io) {
        settings.putBoolean(KEY_DARK, enabled)
    }

    override suspend fun setBestScore(score: Long) = withContext(dispatchers.io) {
        writeMutex.withLock {
            // Monotonic — never overwrite a higher persisted best with a lower one.
            if (score > settings.getLong(KEY_BEST_SCORE, 0L)) {
                settings.putLong(KEY_BEST_SCORE, score)
            }
        }
    }

    override suspend fun incrementReviewPromptCount() = withContext(dispatchers.io) {
        writeMutex.withLock {
            val next = settings.getInt(KEY_REVIEW_PROMPT_COUNT, 0) + 1
            settings.putInt(KEY_REVIEW_PROMPT_COUNT, next)
        }
    }

    override suspend fun suppressReviewPrompts(max: Int) = withContext(dispatchers.io) {
        writeMutex.withLock {
            if (settings.getInt(KEY_REVIEW_PROMPT_COUNT, 0) < max) {
                settings.putInt(KEY_REVIEW_PROMPT_COUNT, max)
            }
        }
    }

    override suspend fun setTutorialSeen() = withContext(dispatchers.io) {
        settings.putBoolean(KEY_TUTORIAL_SEEN, true)
    }

    /**
     * If a legacy single-flag value is present and neither new key has been
     * written, copy the legacy value into both. Idempotent: the legacy key is
     * removed afterwards so this runs at most once per device.
     */
    private fun migrateLegacySoundFlag() {
        if (!settings.hasKey(KEY_SOUND_LEGACY)) return
        val legacy = settings.getBoolean(KEY_SOUND_LEGACY, true)
        if (!settings.hasKey(KEY_MUSIC)) settings.putBoolean(KEY_MUSIC, legacy)
        if (!settings.hasKey(KEY_SFX)) settings.putBoolean(KEY_SFX, legacy)
        settings.remove(KEY_SOUND_LEGACY)
    }

    private companion object {
        const val KEY_MUSIC = "blockblast.music"
        const val KEY_SFX = "blockblast.sfx"
        const val KEY_SOUND_LEGACY = "blockblast.sound"
        const val KEY_VIBRATION = "blockblast.vibration"
        const val KEY_DARK = "blockblast.dark_theme"
        const val KEY_BEST_SCORE = "blockblast.best_score"
        const val KEY_REVIEW_PROMPT_COUNT = "blockblast.review_prompt_count"
        const val KEY_TUTORIAL_SEEN = "blockblast.tutorial_seen"
    }
}
