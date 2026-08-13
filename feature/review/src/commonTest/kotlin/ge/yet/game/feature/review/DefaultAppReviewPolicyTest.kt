package ge.yet.game.feature.review

import ge.yet.game.feature.review.domain.repository.ReviewPromptRepository
import ge.yet.game.feature.review.policy.DefaultAppReviewPolicy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultAppReviewPolicyTest {

    @Test
    fun opportunity_below_limit_is_acquired_and_counted() = runTest {
        val repository = FakeReviewPromptRepository(promptCount = 0)
        val policy = DefaultAppReviewPolicy(repository)

        assertTrue(policy.tryAcquirePrompt())

        assertEquals(1, repository.promptCount.value)
    }

    @Test
    fun opportunity_at_limit_is_rejected_without_increment() = runTest {
        val repository = FakeReviewPromptRepository(promptCount = 2)
        val policy = DefaultAppReviewPolicy(repository)

        assertFalse(policy.tryAcquirePrompt())

        assertEquals(2, repository.promptCount.value)
    }

    @Test
    fun concurrent_opportunities_cannot_exceed_limit() = runTest {
        val repository = FakeReviewPromptRepository(promptCount = 0)
        val policy = DefaultAppReviewPolicy(repository)

        val acquired = List(8) { async { policy.tryAcquirePrompt() } }.awaitAll()

        assertEquals(2, acquired.count { it })
        assertEquals(2, repository.promptCount.value)
    }

    private class FakeReviewPromptRepository(promptCount: Int) : ReviewPromptRepository {
        private val promptCountFlow = MutableStateFlow(promptCount)
        override val promptCount = promptCountFlow.asStateFlow()

        override suspend fun incrementPromptCount() {
            promptCountFlow.value += 1
        }

        override suspend fun suppressPrompts(max: Int) {
            promptCountFlow.value = maxOf(promptCountFlow.value, max)
        }
    }
}
