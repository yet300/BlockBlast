package ge.yet.blokblast.data.repository

import com.app.common.AppDispatchers
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun defaults() {
        assertTrue(repo.soundEnabled.value)
        assertTrue(repo.vibrationEnabled.value)
        assertFalse(repo.darkTheme.value)
        assertEquals(0L, repo.bestScore.value)
        assertEquals(0, repo.reviewPromptCount.value)
        assertFalse(repo.tutorialSeen.value)
    }

    @Test
    fun setSoundEnabled_updates_flow() = runTest {
        repo.setSoundEnabled(false)
        assertFalse(repo.soundEnabled.value)
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
    fun setBestScore_is_monotonic() = runTest {
        repo.setBestScore(100)
        repo.setBestScore(50) // ignored
        assertEquals(100L, repo.bestScore.value)
        repo.setBestScore(200)
        assertEquals(200L, repo.bestScore.value)
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

    @Test
    fun setTutorialSeen() = runTest {
        repo.setTutorialSeen()
        assertTrue(repo.tutorialSeen.value)
    }
}
