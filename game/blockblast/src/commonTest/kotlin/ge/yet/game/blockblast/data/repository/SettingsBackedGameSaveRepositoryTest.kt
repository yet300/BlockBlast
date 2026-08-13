package ge.yet.game.blockblast.data.repository

import com.app.common.AppDispatchers
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import ge.yet.game.blockblast.data.repository.SettingsBackedGameSaveRepository
import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.blockblast.domain.model.Grid
import ge.yet.game.blockblast.domain.model.Piece
import ge.yet.game.blockblast.domain.model.Polyomino
import ge.yet.game.blockblast.domain.model.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SettingsBackedGameSaveRepositoryTest {

    private fun newRepo(settings: Settings = MapSettings()) =
        SettingsBackedGameSaveRepository(
            settings = settings,
            dispatchers = AppDispatchers(
                default = Dispatchers.Unconfined,
                io = Dispatchers.Unconfined,
            ),
        )

    private val sampleState = GameState(
        grid = Grid().withCell(2, 3, 5),
        score = 1234L,
        bestScore = 5000L,
        comboLevel = 2,
        movesWithoutClear = 2,
        currentPieces = listOf(
            Piece(
                pieceId = 7L,
                shape = Polyomino(id = "h2", cells = listOf(Position(0, 0), Position(1, 0))),
                colorId = 3,
            ),
        ),
        isGameOver = false,
        revivesUsed = 0,
        bestAtRoundStart = 5000L,
        reviewPromptFiredThisRound = true,
    )

    @Test
    fun load_returns_null_on_empty_store() = runTest {
        val repo = newRepo()
        assertNull(repo.load())
    }

    @Test
    fun save_then_load_round_trip() = runTest {
        val repo = newRepo()
        repo.save(sampleState)
        val loaded = repo.load()
        assertNotNull(loaded)
        assertEquals(sampleState, loaded)
    }

    @Test
    fun legacy_save_without_moves_without_clear_defaults_to_zero() = runTest {
        val settings = MapSettings(
            "blockblast.game_save" to
                """{"version":1,"state":{"score":1234,"comboLevel":2}}""",
        )

        val loaded = assertNotNull(newRepo(settings).load())

        assertEquals(2, loaded.comboLevel)
        assertEquals(0, loaded.movesWithoutClear)
    }

    @Test
    fun load_returns_null_for_corrupt_json() = runTest {
        val settings = MapSettings("blockblast.game_save" to "{not valid json")
        val repo = newRepo(settings)
        assertNull(repo.load())
    }

    @Test
    fun clear_removes_persisted_save() = runTest {
        val settings = MapSettings()
        val repo = newRepo(settings)
        repo.save(sampleState)
        repo.clear()
        assertNull(repo.load())
        assertNull(newRepo(settings).load())
    }

    @Test
    fun cache_warm_avoids_extra_disk_reads() = runTest {
        val settings = CountingSettings()
        val repo = SettingsBackedGameSaveRepository(
            settings = settings,
            dispatchers = AppDispatchers(
                default = Dispatchers.Unconfined,
                io = Dispatchers.Unconfined,
            ),
        )
        repo.save(sampleState)
        repo.load()
        val readsAfterPrime = settings.readCount
        // Subsequent loads hit the cache.
        repo.load()
        repo.load()
        assertEquals(0, readsAfterPrime)
        assertEquals(readsAfterPrime, settings.readCount)
    }

    @Test
    fun load_returns_null_consistently_after_first_miss() = runTest {
        val settings = MapSettings()
        val repo = newRepo(settings)
        assertNull(repo.load())
        // External writes after a miss aren't picked up — cache locked.
        settings.putString(
            "blockblast.game_save",
            """{"score":1}""",
        )
        assertNull(repo.load())
    }

    @Test
    fun failed_disk_write_does_not_replace_warm_cached_state() = runTest {
        val settings = FailingPutSettings()
        val repo = newRepo(settings)
        val terminalState = sampleState.copy(isGameOver = true)
        repo.save(terminalState)
        assertEquals(terminalState, repo.load())

        settings.failWrites = true
        assertFailsWith<IllegalStateException> {
            repo.save(sampleState.copy(score = 9_999L))
        }

        assertEquals(terminalState, repo.load())
    }

    @Test
    fun cancellation_after_successful_disk_write_keeps_cache_in_sync() = runTest {
        val settings = CancellingPutSettings()
        val repo = newRepo(settings)
        val oldState = sampleState.copy(score = 1_111L)
        repo.save(oldState)
        assertEquals(oldState, repo.load())

        lateinit var saveJob: Job
        settings.afterPut = { saveJob.cancel() }

        val updatedState = sampleState.copy(score = 8_888L)
        saveJob = launch { repo.save(updatedState) }
        saveJob.join()

        assertEquals(true, saveJob.isCancelled)
        assertEquals(updatedState, repo.load())
    }

    @Test
    fun failed_clear_keeps_warm_cached_state() = runTest {
        val settings = FailingRemoveSettings()
        val repo = newRepo(settings)
        repo.save(sampleState)
        assertEquals(sampleState, repo.load())

        settings.failRemoves = true
        assertFailsWith<IllegalStateException> { repo.clear() }

        assertEquals(sampleState, repo.load())
    }

    @Test
    fun caller_mutation_after_save_does_not_change_cached_snapshot() = runTest {
        val repo = newRepo()
        val mutablePieces = sampleState.currentPieces.toMutableList()
        val mutableShapeCells = sampleState.currentPieces.single().shape.cells.toMutableList()
        val mutableClearedCells = mutableListOf(Position(4, 5))
        mutablePieces[0] = mutablePieces.single().copy(
            shape = mutablePieces.single().shape.copy(cells = mutableShapeCells),
        )
        val state = sampleState.copy(
            currentPieces = mutablePieces,
            lastClearedCells = sampleState.lastClearedCells.copy(cells = mutableClearedCells),
        )
        repo.save(state)

        state.grid.cells[state.grid.index(2, 3)] = Grid.EMPTY
        mutablePieces.clear()
        mutableShapeCells.clear()
        mutableClearedCells.clear()

        val loaded = assertNotNull(repo.load())
        assertEquals(5, loaded.grid.colorAt(2, 3))
        assertEquals(1, loaded.currentPieces.size)
        assertEquals(2, loaded.currentPieces.single().shape.cells.size)
        assertEquals(listOf(Position(4, 5)), loaded.lastClearedCells.cells)
    }

    @Test
    fun caller_mutation_after_load_does_not_change_future_cached_reads() = runTest {
        val repo = newRepo()
        repo.save(
            sampleState.copy(
                lastClearedCells = sampleState.lastClearedCells.copy(
                    cells = listOf(Position(4, 5)),
                ),
            ),
        )

        val first = assertNotNull(repo.load())
        first.grid.cells[first.grid.index(2, 3)] = Grid.EMPTY

        val second = assertNotNull(repo.load())
        assertEquals(5, second.grid.colorAt(2, 3))
        assertEquals(1, second.currentPieces.size)
        assertEquals(2, second.currentPieces.single().shape.cells.size)
        assertEquals(listOf(Position(4, 5)), second.lastClearedCells.cells)
        assertFalse(first.currentPieces === second.currentPieces)
        assertFalse(
            first.currentPieces.single().shape.cells ===
                second.currentPieces.single().shape.cells,
        )
        assertFalse(first.lastClearedCells.cells === second.lastClearedCells.cells)
    }

    /** Wraps MapSettings to count getStringOrNull invocations. */
    private class CountingSettings(
        private val delegate: MapSettings = MapSettings(),
    ) : Settings by delegate {
        var readCount = 0
        override fun getStringOrNull(key: String): String? {
            readCount += 1
            return delegate.getStringOrNull(key)
        }
    }

    private class FailingPutSettings(
        private val delegate: MapSettings = MapSettings(),
    ) : Settings by delegate {
        var failWrites: Boolean = false

        override fun putString(key: String, value: String) {
            if (failWrites) error("disk write failed")
            delegate.putString(key, value)
        }
    }

    private class CancellingPutSettings(
        private val delegate: MapSettings = MapSettings(),
    ) : Settings by delegate {
        var afterPut: () -> Unit = {}

        override fun putString(key: String, value: String) {
            delegate.putString(key, value)
            afterPut()
        }
    }

    private class FailingRemoveSettings(
        private val delegate: MapSettings = MapSettings(),
    ) : Settings by delegate {
        var failRemoves: Boolean = false

        override fun remove(key: String) {
            if (failRemoves) error("disk remove failed")
            delegate.remove(key)
        }
    }
}
