package ge.yet.game.feature.review

import ge.yet.game.domain.repository.SettingsRepository
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
        val settings = FakeSettings(reviewCount = 0)
        val policy = DefaultAppReviewPolicy(settings)

        assertTrue(policy.tryAcquirePrompt())

        assertEquals(1, settings.reviewPromptCount.value)
    }

    @Test
    fun opportunity_at_limit_is_rejected_without_increment() = runTest {
        val settings = FakeSettings(reviewCount = 2)
        val policy = DefaultAppReviewPolicy(settings)

        assertFalse(policy.tryAcquirePrompt())

        assertEquals(2, settings.reviewPromptCount.value)
    }

    @Test
    fun concurrent_opportunities_cannot_exceed_limit() = runTest {
        val settings = FakeSettings(reviewCount = 0)
        val policy = DefaultAppReviewPolicy(settings)

        val acquired = List(8) { async { policy.tryAcquirePrompt() } }.awaitAll()

        assertEquals(2, acquired.count { it })
        assertEquals(2, settings.reviewPromptCount.value)
    }

    private class FakeSettings(reviewCount: Int) : SettingsRepository {
        private val reviewCountFlow = MutableStateFlow(reviewCount)
        override val musicEnabled = MutableStateFlow(true).asStateFlow()
        override val sfxEnabled = MutableStateFlow(true).asStateFlow()
        override val vibrationEnabled = MutableStateFlow(true).asStateFlow()
        override val darkTheme = MutableStateFlow(false).asStateFlow()
        override val adsEnabled = MutableStateFlow(true).asStateFlow()
        override val bestScore = MutableStateFlow(0L).asStateFlow()
        override val reviewPromptCount = reviewCountFlow.asStateFlow()
        override suspend fun setMusicEnabled(enabled: Boolean) = Unit
        override suspend fun setSfxEnabled(enabled: Boolean) = Unit
        override suspend fun setVibrationEnabled(enabled: Boolean) = Unit
        override suspend fun setDarkTheme(enabled: Boolean) = Unit
        override suspend fun setAdsEnabled(enabled: Boolean) = Unit
        override suspend fun setBestScore(score: Long) = Unit
        override suspend fun incrementReviewPromptCount() {
            reviewCountFlow.value += 1
        }
        override suspend fun suppressReviewPrompts(max: Int) {
            reviewCountFlow.value = maxOf(reviewCountFlow.value, max)
        }
    }
}
