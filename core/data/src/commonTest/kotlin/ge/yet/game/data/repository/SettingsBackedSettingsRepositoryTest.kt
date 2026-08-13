package ge.yet.game.data.repository

import com.app.common.AppDispatchers
import com.russhwolf.settings.MapSettings
import ge.yet.game.data.repository.SettingsBackedSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsBackedSettingsRepositoryTest {

    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    private val settings = MapSettings()
    private val repo = SettingsBackedSettingsRepository(
        settings = settings,
        scope = scope,
        dispatchers = AppDispatchers(
            default = Dispatchers.Unconfined,
            io = Dispatchers.Unconfined,
        ),
    )

    @AfterTest
    fun tearDown() {
        scope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun defaults() {
        assertTrue(repo.musicEnabled.value)
        assertTrue(repo.sfxEnabled.value)
        assertTrue(repo.vibrationEnabled.value)
        assertFalse(repo.darkTheme.value)
        assertTrue(repo.adsEnabled.value)
        assertEquals(0, repo.reviewPromptCount.value)
    }

    @Test
    fun ads_preference_survives_repository_recreation() = runTest {
        repo.setAdsEnabled(false)
        assertFalse(repo.adsEnabled.value)

        val recreated = SettingsBackedSettingsRepository(
            settings = settings,
            scope = scope,
            dispatchers = AppDispatchers(
                default = Dispatchers.Unconfined,
                io = Dispatchers.Unconfined,
            ),
        )
        assertFalse(recreated.adsEnabled.value)

        recreated.setAdsEnabled(true)
        assertTrue(repo.adsEnabled.value)
    }

    @Test
    fun setMusicEnabled_updates_flow_without_touching_sfx() = runTest {
        repo.setMusicEnabled(false)
        assertFalse(repo.musicEnabled.value)
        assertTrue(repo.sfxEnabled.value)
    }

    @Test
    fun setSfxEnabled_updates_flow_without_touching_music() = runTest {
        repo.setSfxEnabled(false)
        assertFalse(repo.sfxEnabled.value)
        assertTrue(repo.musicEnabled.value)
    }

    @Test
    fun migrates_legacy_sound_flag_into_both_keys() = runTest {
        // Simulate an upgrade from a pre-1.5 install with sound = false.
        val legacySettings = MapSettings().apply { putBoolean("blockblast.sound", false) }
        val migrated = SettingsBackedSettingsRepository(
            settings = legacySettings,
            scope = scope,
            dispatchers = AppDispatchers(
                default = Dispatchers.Unconfined,
                io = Dispatchers.Unconfined
            ),
        )
        assertFalse(migrated.musicEnabled.value)
        assertFalse(migrated.sfxEnabled.value)
    }

    @Test
    fun migration_runs_only_once() = runTest {
        val sharedSettings = MapSettings().apply { putBoolean("blockblast.sound", false) }
        SettingsBackedSettingsRepository(
            sharedSettings,
            scope,
            AppDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined)
        )
        // User re-enables music explicitly after migration.
        sharedSettings.putBoolean("blockblast.music", true)
        // Second construction (e.g. process restart) must not overwrite that.
        val again = SettingsBackedSettingsRepository(
            sharedSettings,
            scope,
            AppDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined)
        )
        assertTrue(again.musicEnabled.value)
    }

    @Test
    fun setVibrationEnabled_updates_flow() = runTest {
        repo.setVibrationEnabled(false)
        assertFalse(repo.vibrationEnabled.value)
    }

    @Test
    fun setDarkTheme_updates_flow() = runTest {
        repo.setDarkTheme(true)
        assertTrue(repo.darkTheme.value)
    }

    @Test
    fun incrementReviewPromptCount_serial() = runTest {
        repo.incrementReviewPromptCount()
        repo.incrementReviewPromptCount()
        repo.incrementReviewPromptCount()
        assertEquals(3, repo.reviewPromptCount.value)
    }

    @Test
    fun incrementReviewPromptCount_concurrent_no_lost_updates() = runTest {
        val jobs = List(50) {
            async(Dispatchers.Unconfined) { repo.incrementReviewPromptCount() }
        }
        jobs.awaitAll()
        assertEquals(50, repo.reviewPromptCount.value)
    }

    // ── Race-condition / mutex coverage ──────────────────────────────────
    //
    // These tests run many writers concurrently against the same key. Without
    // writeMutex, incrementReviewPromptCount / suppressReviewPrompts could
    // interleave and produce lost updates.

    @Test
    fun suppressReviewPrompts_concurrent_callers_settle_at_max() = runTest {
        // Multiple parallel suppress calls — idempotent, must end exactly at max.
        List(25) { async(Dispatchers.Unconfined) { repo.suppressReviewPrompts(max = 3) } }
            .awaitAll()
        assertEquals(3, repo.reviewPromptCount.value)
    }

    @Test
    fun suppressReviewPrompts_no_op_when_already_at_or_above_max() = runTest {
        repeat(5) { repo.incrementReviewPromptCount() } // count = 5
        repo.suppressReviewPrompts(max = 3) // already above
        assertEquals(5, repo.reviewPromptCount.value)
        repo.suppressReviewPrompts(max = 5) // equal to max
        assertEquals(5, repo.reviewPromptCount.value)
    }

    @Test
    fun increment_and_suppress_concurrent_never_drops_below_max() = runTest {
        // Mix N increments with one suppress(max=10). After everything settles,
        // count must be at least max — the suppress floor must hold even when
        // increments race against it.
        val increments = List(20) {
            async(Dispatchers.Unconfined) { repo.incrementReviewPromptCount() }
        }
        val suppress = async(Dispatchers.Unconfined) { repo.suppressReviewPrompts(max = 10) }
        (increments + suppress).awaitAll()
        assertTrue(
            repo.reviewPromptCount.value >= 10,
            "expected count ≥ 10 after concurrent suppress+increment, got ${repo.reviewPromptCount.value}",
        )
    }
}
