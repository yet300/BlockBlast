package ge.yet.game.feature.review.data.repository

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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsBackedReviewPromptRepositoryTest {

    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    private val settings = MapSettings()
    private val repository = SettingsBackedReviewPromptRepository(
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
        assertEquals(0, repository.promptCount.value)
    }

    @Test
    fun reads_count_written_by_previous_app_version() {
        settings.putInt("blockblast.review_prompt_count", 1)

        assertEquals(1, repository.promptCount.value)
    }

    @Test
    fun concurrent_increments_do_not_lose_updates() = runTest {
        List(50) {
            async(Dispatchers.Unconfined) { repository.incrementPromptCount() }
        }.awaitAll()

        assertEquals(50, repository.promptCount.value)
    }

    @Test
    fun suppress_sets_a_floor_without_lowering_existing_count() = runTest {
        repository.suppressPrompts(max = 3)
        assertEquals(3, repository.promptCount.value)

        repeat(2) { repository.incrementPromptCount() }
        repository.suppressPrompts(max = 3)

        assertEquals(5, repository.promptCount.value)
    }

    @Test
    fun decrement_returns_a_reserved_prompt_without_going_below_zero() = runTest {
        repository.incrementPromptCount()

        repository.decrementPromptCount()
        repository.decrementPromptCount()

        assertEquals(0, repository.promptCount.value)
    }

    @Test
    fun concurrent_increment_and_suppress_never_finish_below_limit() = runTest {
        val increments = List(20) {
            async(Dispatchers.Unconfined) { repository.incrementPromptCount() }
        }
        val suppress = async(Dispatchers.Unconfined) {
            repository.suppressPrompts(max = 10)
        }

        (increments + suppress).awaitAll()

        assertTrue(repository.promptCount.value >= 10)
    }
}
