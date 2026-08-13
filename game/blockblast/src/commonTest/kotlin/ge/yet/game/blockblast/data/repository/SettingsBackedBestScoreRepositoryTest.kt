package ge.yet.game.blockblast.data.repository

import com.app.common.AppDispatchers
import com.russhwolf.settings.MapSettings
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsBackedBestScoreRepositoryTest {

    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    private val settings = MapSettings()
    private val repository = SettingsBackedBestScoreRepository(
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
    fun defaults_to_zero() {
        assertEquals(0L, repository.bestScore.value)
    }

    @Test
    fun reads_score_written_by_previous_app_version() {
        settings.putLong("blockblast.best_score", 7_500L)

        assertEquals(7_500L, repository.bestScore.value)
    }

    @Test
    fun setBestScore_is_monotonic() = runTest {
        repository.setBestScore(100L)
        repository.setBestScore(50L)
        repository.setBestScore(200L)

        assertEquals(200L, repository.bestScore.value)
    }

    @Test
    fun concurrent_writers_keep_maximum_score() = runTest {
        (1L..100L)
            .shuffled()
            .map { score ->
                async(Dispatchers.Unconfined) {
                    repository.setBestScore(score)
                }
            }
            .awaitAll()

        assertEquals(100L, repository.bestScore.value)
    }
}
