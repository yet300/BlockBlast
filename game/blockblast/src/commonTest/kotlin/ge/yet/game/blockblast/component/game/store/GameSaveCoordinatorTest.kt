package ge.yet.game.blockblast.component.game.store

import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.blockblast.domain.repository.GameSaveRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GameSaveCoordinatorTest {

    @Test
    fun schedule_debounces_to_the_latest_snapshot() = runTest {
        val repository = RecordingSaveRepository()
        val coordinator = GameSaveCoordinator(repository, debounceMillis = 300)

        coordinator.schedule(this, GameState(score = 1))
        advanceTimeBy(100)
        coordinator.schedule(this, GameState(score = 2))
        advanceTimeBy(299)
        runCurrent()
        assertEquals(emptyList(), repository.saved)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(2L), repository.saved.map(GameState::score))
    }

    @Test
    fun flush_invalidates_pending_save_before_writing_explicit_snapshot() = runTest {
        val repository = RecordingSaveRepository()
        val coordinator = GameSaveCoordinator(repository, debounceMillis = 300)

        coordinator.schedule(this, GameState(score = 1))
        coordinator.flush(GameState(score = 9))
        advanceTimeBy(300)
        runCurrent()

        assertEquals(listOf(9L), repository.saved.map(GameState::score))
    }

    private class RecordingSaveRepository : GameSaveRepository {
        val saved = mutableListOf<GameState>()

        override suspend fun save(state: GameState) {
            saved += state
        }

        override suspend fun load(): GameState? = saved.lastOrNull()

        override suspend fun clear() {
            saved.clear()
        }
    }
}
