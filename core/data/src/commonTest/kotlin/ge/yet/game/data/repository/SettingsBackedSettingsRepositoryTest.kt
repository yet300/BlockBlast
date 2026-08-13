package ge.yet.game.data.repository

import com.app.common.AppDispatchers
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
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

}
