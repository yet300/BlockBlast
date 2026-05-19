package ge.yet.blokblast.data.repository

import ge.yet.blokblast.data.platform.PlatformVibrator
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultVibrationRepositoryTest {

    @Test
    fun vibrate_light_calls_platform_when_enabled() = runTest {
        val v = RecordingVibrator()
        val repo = DefaultVibrationRepository(v, FakeSettings(true))
        repo.vibrateLight()
        assertTrue(v.lightCalled)
        assertFalse(v.heavyCalled)
    }

    @Test
    fun vibrate_heavy_calls_platform_when_enabled() = runTest {
        val v = RecordingVibrator()
        val repo = DefaultVibrationRepository(v, FakeSettings(true))
        repo.vibrateHeavy()
        assertTrue(v.heavyCalled)
        assertFalse(v.lightCalled)
    }

    @Test
    fun no_calls_when_disabled() = runTest {
        val v = RecordingVibrator()
        val repo = DefaultVibrationRepository(v, FakeSettings(false))
        repo.vibrateLight()
        repo.vibrateHeavy()
        assertFalse(v.lightCalled)
        assertFalse(v.heavyCalled)
    }

    @Test
    fun gating_reads_flag_live() = runTest {
        val v = RecordingVibrator()
        val settings = FakeSettings(true)
        val repo = DefaultVibrationRepository(v, settings)
        repo.vibrateLight()
        assertEquals(1, v.lightCount)
        settings.vibrationFlow.value = false
        repo.vibrateLight()
        assertEquals(1, v.lightCount)
    }

    private class RecordingVibrator : PlatformVibrator {
        var lightCalled = false
        var heavyCalled = false
        var lightCount = 0
        override fun light() { lightCalled = true; lightCount += 1 }
        override fun heavy() { heavyCalled = true }
    }

    private class FakeSettings(vibration: Boolean) : SettingsRepository {
        val vibrationFlow = MutableStateFlow(vibration)
        override val musicEnabled = MutableStateFlow(true).asStateFlow()
        override val sfxEnabled = MutableStateFlow(true).asStateFlow()
        override val vibrationEnabled: StateFlow<Boolean> = vibrationFlow.asStateFlow()
        override val darkTheme = MutableStateFlow(false).asStateFlow()
        override val bestScore = MutableStateFlow(0L).asStateFlow()
        override val reviewPromptCount = MutableStateFlow(0).asStateFlow()
        override val tutorialSeen = MutableStateFlow(false).asStateFlow()
        override suspend fun setMusicEnabled(enabled: Boolean) {}
        override suspend fun setSfxEnabled(enabled: Boolean) {}
        override suspend fun setVibrationEnabled(enabled: Boolean) { vibrationFlow.value = enabled }
        override suspend fun setDarkTheme(enabled: Boolean) {}
        override suspend fun setBestScore(score: Long) {}
        override suspend fun incrementReviewPromptCount() {}
        override suspend fun suppressReviewPrompts(max: Int) {}
        override suspend fun setTutorialSeen() {}
    }
}
