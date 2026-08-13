package ge.yet.game.feature.review.policy

import com.app.common.config.AppConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.feature.review.domain.repository.ReviewPromptRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@SingleIn(AppScope::class)
@Inject
internal class DefaultAppReviewPolicy(
    private val reviewPromptRepository: ReviewPromptRepository,
) : AppReviewPolicy {

    private val mutex = Mutex()

    override suspend fun tryAcquirePrompt(): Boolean = mutex.withLock {
        if (reviewPromptRepository.promptCount.value >= AppConfig.REVIEW_MAX_PROMPTS) {
            false
        } else {
            reviewPromptRepository.incrementPromptCount()
            true
        }
    }
}
