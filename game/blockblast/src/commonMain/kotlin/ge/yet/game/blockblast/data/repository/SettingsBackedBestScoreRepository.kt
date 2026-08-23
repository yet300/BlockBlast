package ge.yet.game.blockblast.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.blockblast.domain.repository.BestScoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Block Blast-owned best-score storage. Retains the legacy key for upgrades. */
@SingleIn(AppScope::class)
@Inject
internal class SettingsBackedBestScoreRepository(
    private val storage: BlockBlastStorage,
    scope: CoroutineScope,
) : BestScoreRepository {

    private val writeMutex = Mutex()

    override val bestScore: StateFlow<Long> =
        storage.observeLong(KEY_BEST_SCORE, defaultValue = 0L)
            .stateIn(scope, SharingStarted.Eagerly, 0L)

    override suspend fun setBestScore(score: Long) {
        writeMutex.withLock {
            if (score > storage.getLong(KEY_BEST_SCORE, 0L)) {
                storage.putLong(KEY_BEST_SCORE, score)
            }
        }
    }

    private companion object {
        const val KEY_BEST_SCORE = "best_score"
    }
}
