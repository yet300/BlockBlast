package ge.yet.game.feature.review

import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.feature.review.domain.repository.ReviewPromptRepository
import ge.yet.game.feature.review.policy.DefaultAppReviewPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultAppReviewPolicyTest {

    @Test
    fun opportunity_below_limit_is_acquired_and_counted() = runTest {
        val repository = FakeReviewPromptRepository(promptCount = 0)
        val policy = DefaultAppReviewPolicy(repository, RecordingAnalytics())

        assertTrue(policy.tryAcquirePrompt())

        assertEquals(1, repository.promptCount.value)
    }

    @Test
    fun opportunity_at_limit_is_rejected_without_increment() = runTest {
        val repository = FakeReviewPromptRepository(promptCount = 2)
        val policy = DefaultAppReviewPolicy(repository, RecordingAnalytics())

        assertFalse(policy.tryAcquirePrompt())

        assertEquals(2, repository.promptCount.value)
    }

    @Test
    fun concurrent_opportunities_cannot_exceed_limit() = runTest {
        val repository = FakeReviewPromptRepository(promptCount = 0)
        val policy = DefaultAppReviewPolicy(repository, RecordingAnalytics())

        val acquired = List(8) { async { policy.tryAcquirePrompt() } }.awaitAll()

        assertEquals(2, acquired.count { it })
        assertEquals(2, repository.promptCount.value)
    }

    @Test
    fun persistence_failure_rejects_opportunity_and_is_reported() = runTest {
        val analytics = RecordingAnalytics()
        val policy = DefaultAppReviewPolicy(
            reviewPromptRepository = ThrowingReviewPromptRepository(IllegalStateException("write failed")),
            analytics = analytics,
        )

        assertFalse(policy.tryAcquirePrompt())

        assertEquals(listOf("review_prompt_acquire_failed"), analytics.eventNames)
    }

    @Test
    fun cancellation_is_not_converted_to_policy_rejection() = runTest {
        val policy = DefaultAppReviewPolicy(
            reviewPromptRepository = ThrowingReviewPromptRepository(CancellationException("cancelled")),
            analytics = RecordingAnalytics(),
        )

        assertFailsWith<CancellationException> { policy.tryAcquirePrompt() }
    }

    @Test
    fun released_opportunity_returns_reserved_count() = runTest {
        val repository = FakeReviewPromptRepository(promptCount = 0)
        val policy = DefaultAppReviewPolicy(repository, RecordingAnalytics())

        assertTrue(policy.tryAcquirePrompt())
        policy.releasePrompt()

        assertEquals(0, repository.promptCount.value)
    }

    private class FakeReviewPromptRepository(promptCount: Int) : ReviewPromptRepository {
        private val promptCountFlow = MutableStateFlow(promptCount)
        override val promptCount = promptCountFlow.asStateFlow()

        override suspend fun incrementPromptCount() {
            promptCountFlow.value += 1
        }

        override suspend fun decrementPromptCount() {
            promptCountFlow.value = (promptCountFlow.value - 1).coerceAtLeast(0)
        }

        override suspend fun suppressPrompts(max: Int) {
            promptCountFlow.value = maxOf(promptCountFlow.value, max)
        }
    }

    private class ThrowingReviewPromptRepository(
        private val error: Exception,
    ) : ReviewPromptRepository {
        override val promptCount = MutableStateFlow(0).asStateFlow()
        override suspend fun incrementPromptCount() = throw error
        override suspend fun decrementPromptCount() = Unit
        override suspend fun suppressPrompts(max: Int) = Unit
    }

    private class RecordingAnalytics : AnalyticRepository {
        val eventNames = mutableListOf<String>()

        override fun logEvent(eventName: String, params: Map<String, Any>?) {
            eventNames += eventName
        }

        override fun deleteData() = Unit
    }
}
