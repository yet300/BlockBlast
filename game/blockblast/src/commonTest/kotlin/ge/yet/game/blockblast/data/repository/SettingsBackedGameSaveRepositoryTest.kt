package ge.yet.game.blockblast.data.repository

import ge.yet.game.blockblast.data.repository.SettingsBackedGameSaveRepository
import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.blockblast.domain.model.Grid
import ge.yet.game.blockblast.domain.model.Piece
import ge.yet.game.blockblast.domain.model.Polyomino
import ge.yet.game.blockblast.domain.model.Position
import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.testkit.MutableMiniAppStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsBackedGameSaveRepositoryTest {

    private fun newRepo(storage: MiniAppStorage = MutableMiniAppStorage()) =
        SettingsBackedGameSaveRepository(BlockBlastStorage(storage))

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
    fun hasSavedGame_returns_true_for_playable_save() = runTest {
        val repo = newRepo()
        repo.save(sampleState)

        assertTrue(repo.hasSavedGame())
    }

    @Test
    fun hasSavedGame_returns_false_for_game_over_save() = runTest {
        val repo = newRepo()
        repo.save(sampleState.copy(isGameOver = true))

        assertFalse(repo.hasSavedGame())
    }

    @Test
    fun hasSavedGame_returns_false_for_empty_board() = runTest {
        val repo = newRepo()
        repo.save(sampleState.copy(grid = Grid()))

        assertFalse(repo.hasSavedGame())
    }

    @Test
    fun legacy_save_without_moves_without_clear_defaults_to_zero() = runTest {
        val storage = MutableMiniAppStorage(
            mapOf("game_save" to
                """{"version":1,"state":{"score":1234,"comboLevel":2}}""",
            ),
        )

        val loaded = assertNotNull(newRepo(storage).load())

        assertEquals(2, loaded.comboLevel)
        assertEquals(0, loaded.movesWithoutClear)
    }

    @Test
    fun load_returns_null_for_corrupt_json() = runTest {
        val storage = MutableMiniAppStorage(mapOf("game_save" to "{not valid json"))
        val repo = newRepo(storage)
        assertNull(repo.load())
    }

    @Test
    fun clear_removes_persisted_save() = runTest {
        val storage = MutableMiniAppStorage()
        val repo = newRepo(storage)
        repo.save(sampleState)
        repo.clear()
        assertNull(repo.load())
        assertNull(newRepo(storage).load())
    }

    @Test
    fun warm_repository_observes_external_clear() = runTest {
        val storage = MutableMiniAppStorage()
        val repo = newRepo(storage)
        repo.save(sampleState)
        assertEquals(sampleState, repo.load())

        storage.remove("game_save")

        assertNull(repo.load())
    }

    @Test
    fun load_observes_external_write_after_first_miss() = runTest {
        val storage = MutableMiniAppStorage()
        val repo = newRepo(storage)
        assertNull(repo.load())

        val writer = newRepo(storage)
        writer.save(sampleState)

        assertEquals(sampleState, repo.load())
    }

    @Test
    fun failed_write_keeps_previous_persisted_state() = runTest {
        val storage = FailingPutStorage()
        val repo = newRepo(storage)
        val terminalState = sampleState.copy(isGameOver = true)
        repo.save(terminalState)
        assertEquals(terminalState, repo.load())

        storage.failWrites = true
        assertFailsWith<IllegalStateException> {
            repo.save(sampleState.copy(score = 9_999L))
        }

        assertEquals(terminalState, repo.load())
    }

    @Test
    fun cancellation_after_successful_write_persists_updated_state() = runTest {
        val storage = CancellingPutStorage()
        val repo = newRepo(storage)
        val oldState = sampleState.copy(score = 1_111L)
        repo.save(oldState)
        assertEquals(oldState, repo.load())

        lateinit var saveJob: Job
        storage.afterPut = { saveJob.cancel() }

        val updatedState = sampleState.copy(score = 8_888L)
        saveJob = launch { repo.save(updatedState) }
        saveJob.join()

        assertEquals(true, saveJob.isCancelled)
        assertEquals(updatedState, repo.load())
    }

    @Test
    fun failed_clear_keeps_persisted_state() = runTest {
        val storage = FailingRemoveStorage()
        val repo = newRepo(storage)
        repo.save(sampleState)
        assertEquals(sampleState, repo.load())

        storage.failRemoves = true
        assertFailsWith<IllegalStateException> { repo.clear() }

        assertEquals(sampleState, repo.load())
    }

    @Test
    fun caller_mutation_after_save_does_not_change_persisted_snapshot() = runTest {
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
    fun caller_mutation_after_load_does_not_change_future_reads() = runTest {
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

    private class FailingPutStorage(
        private val delegate: MutableMiniAppStorage = MutableMiniAppStorage(),
    ) : MiniAppStorage by delegate {
        var failWrites: Boolean = false

        override suspend fun putString(localName: String, value: String) {
            if (failWrites) error("disk write failed")
            delegate.putString(localName, value)
        }
    }

    private class CancellingPutStorage(
        private val delegate: MutableMiniAppStorage = MutableMiniAppStorage(),
    ) : MiniAppStorage by delegate {
        var afterPut: () -> Unit = {}

        override suspend fun putString(localName: String, value: String) {
            delegate.putString(localName, value)
            afterPut()
        }
    }

    private class FailingRemoveStorage(
        private val delegate: MutableMiniAppStorage = MutableMiniAppStorage(),
    ) : MiniAppStorage by delegate {
        var failRemoves: Boolean = false

        override suspend fun remove(localName: String) {
            if (failRemoves) error("disk remove failed")
            delegate.remove(localName)
        }
    }
}
