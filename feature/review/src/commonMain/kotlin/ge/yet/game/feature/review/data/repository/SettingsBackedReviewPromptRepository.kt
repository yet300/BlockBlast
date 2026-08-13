package ge.yet.game.feature.review.data.repository

import com.app.common.AppDispatchers
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getIntStateFlow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.feature.review.domain.repository.ReviewPromptRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Review-feature storage. Retains the legacy key so existing limits survive upgrades. */
@OptIn(ExperimentalSettingsApi::class)
@SingleIn(AppScope::class)
@Inject
internal class SettingsBackedReviewPromptRepository(
    private val settings: ObservableSettings,
    scope: CoroutineScope,
    private val dispatchers: AppDispatchers,
) : ReviewPromptRepository {

    private val writeMutex = Mutex()

    override val promptCount: StateFlow<Int> =
        settings.getIntStateFlow(scope, KEY_PROMPT_COUNT, defaultValue = 0)

    override suspend fun incrementPromptCount() = withContext(dispatchers.io) {
        writeMutex.withLock {
            val next = settings.getInt(KEY_PROMPT_COUNT, 0) + 1
            settings.putInt(KEY_PROMPT_COUNT, next)
        }
    }

    override suspend fun decrementPromptCount() = withContext(dispatchers.io) {
        writeMutex.withLock {
            val next = (settings.getInt(KEY_PROMPT_COUNT, 0) - 1).coerceAtLeast(0)
            settings.putInt(KEY_PROMPT_COUNT, next)
        }
    }

    override suspend fun suppressPrompts(max: Int) = withContext(dispatchers.io) {
        writeMutex.withLock {
            if (settings.getInt(KEY_PROMPT_COUNT, 0) < max) {
                settings.putInt(KEY_PROMPT_COUNT, max)
            }
        }
    }

    private companion object {
        const val KEY_PROMPT_COUNT = "blockblast.review_prompt_count"
    }
}
