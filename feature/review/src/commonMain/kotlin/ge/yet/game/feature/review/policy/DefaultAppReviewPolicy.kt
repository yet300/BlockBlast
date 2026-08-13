package ge.yet.game.feature.review.policy

import com.app.common.config.AppConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.feature.review.domain.repository.ReviewPromptRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@SingleIn(AppScope::class)
@Inject
internal class DefaultAppReviewPolicy(
    private val reviewPromptRepository: ReviewPromptRepository,
    private val analytics: AnalyticRepository,
) : AppReviewPolicy {

    private val mutex = Mutex()

    override suspend fun tryAcquirePrompt(): Boolean = runPersistence(
        failureEvent = "review_prompt_acquire_failed",
        fallback = false,
    ) {
        mutex.withLock {
            if (reviewPromptRepository.promptCount.value >= AppConfig.REVIEW_MAX_PROMPTS) {
                false
            } else {
                reviewPromptRepository.incrementPromptCount()
                true
            }
        }
    }

    override suspend fun releasePrompt() {
        runPersistence(
            failureEvent = "review_prompt_release_failed",
            fallback = Unit,
        ) {
            mutex.withLock {
                reviewPromptRepository.decrementPromptCount()
            }
        }
    }

    private suspend fun <T> runPersistence(
        failureEvent: String,
        fallback: T,
        block: suspend () -> T,
    ): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        runCatching { analytics.logEvent(failureEvent, params = null) }
        fallback
    }
}
