package ge.yet.game.blockblast.data.repository

import ge.yet.game.miniapp.testkit.MutableMiniAppStorage
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
    private val storage = MutableMiniAppStorage()
    private val repository = SettingsBackedBestScoreRepository(
        storage = BlockBlastStorage(storage),
        scope = scope,
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
    fun observes_score_written_through_the_legacy_local_name() = runTest {
        storage.putLong("best_score", 7_500L)

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
