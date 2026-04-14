package ge.yet.blokblast.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.domain.model.GameState
import ge.yet.blokblast.domain.repository.GameSaveRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory save store. Lives only for the current process — no persistence
 * across launches. Used while [SettingsGameSaveRepository] is disabled.
 *
 * `internal` — consumers only see [GameSaveRepository] through the DI graph.
 */
@SingleIn(AppScope::class)
@Inject
internal class LocalGameSaveRepository : GameSaveRepository {
    private val mutex = Mutex()
    private var cached: GameState? = null

    override suspend fun save(state: GameState) = mutex.withLock {
        cached = state
    }

    override suspend fun load(): GameState? = mutex.withLock { cached }

    override suspend fun clear() = mutex.withLock { cached = null }
}
