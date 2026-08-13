package ge.yet.game.feature.review.policy

import com.app.common.config.AppConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.domain.repository.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@SingleIn(AppScope::class)
@Inject
internal class DefaultAppReviewPolicy(
    private val settings: SettingsRepository,
) : AppReviewPolicy {

    private val mutex = Mutex()

    override suspend fun tryAcquirePrompt(): Boolean = mutex.withLock {
        if (settings.reviewPromptCount.value >= AppConfig.REVIEW_MAX_PROMPTS) {
            false
        } else {
            settings.incrementReviewPromptCount()
            true
        }
    }
}
