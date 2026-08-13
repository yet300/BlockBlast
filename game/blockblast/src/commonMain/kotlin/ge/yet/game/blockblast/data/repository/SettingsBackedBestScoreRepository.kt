package ge.yet.game.blockblast.data.repository

import com.app.common.AppDispatchers
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getLongStateFlow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.blockblast.domain.repository.BestScoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Block Blast-owned best-score storage. Retains the legacy key for upgrades. */
@OptIn(ExperimentalSettingsApi::class)
@SingleIn(AppScope::class)
@Inject
internal class SettingsBackedBestScoreRepository(
    private val settings: ObservableSettings,
    scope: CoroutineScope,
    private val dispatchers: AppDispatchers,
) : BestScoreRepository {

    private val writeMutex = Mutex()

    override val bestScore: StateFlow<Long> =
        settings.getLongStateFlow(scope, KEY_BEST_SCORE, defaultValue = 0L)

    override suspend fun setBestScore(score: Long) = withContext(dispatchers.io) {
        writeMutex.withLock {
            if (score > settings.getLong(KEY_BEST_SCORE, 0L)) {
                settings.putLong(KEY_BEST_SCORE, score)
            }
        }
    }

    private companion object {
        const val KEY_BEST_SCORE = "blockblast.best_score"
    }
}
