package ge.yet.game.blockblast.component.game.store

import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.game.blockblast.domain.engine.GameSessionReducer
import ge.yet.game.blockblast.domain.engine.ScoreCalculator
import ge.yet.game.blockblast.domain.engine.ShapeGenerator
import ge.yet.game.blockblast.data.audio.BlockBlastAudio
import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.blockblast.domain.model.Grid
import ge.yet.game.blockblast.domain.model.Piece
import ge.yet.game.blockblast.domain.model.Polyomino
import ge.yet.game.blockblast.domain.model.Position
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.domain.repository.AudioRepository
import ge.yet.game.blockblast.domain.repository.GameSaveRepository
import ge.yet.game.blockblast.domain.repository.BlockBlastTutorialRepository
import ge.yet.game.blockblast.domain.repository.BestScoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameStoreFactoryTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun bootstrap_new_game_dispatches_playable_state_and_logs_source() = runTest {
        val deps = TestDependencies()
        val store = deps.factory.create(isNewGame = true, newGameSeed = 11)
        runCurrent()

        assertTrue(store.state.currentPieces.isNotEmpty())
        assertFalse(store.state.isGameOver)
        assertTrue(deps.analytics.has("game_started", "source" to "new"))
        assertEquals(1, deps.audio.startMusicCount)
        assertEquals(listOf(BlockBlastAudio.musicTracks), deps.audio.playlists)
    }

    @Test
    fun bootstrap_continue_restores_save_and_merges_persisted_best() = runTest {
        val saved = playableState(score = 50).copy(bestScore = 70, bestAtRoundStart = 70)
        val deps = TestDependencies(saved = saved, bestScore = 100)
        val store = deps.factory.create(isNewGame = false)
        runCurrent()

        assertEquals(50, store.state.score)
        assertEquals(100, store.state.bestScore)
        assertEquals(100, store.state.bestAtRoundStart)
        assertTrue(deps.analytics.has("game_started", "source" to "continue"))
    }

    @Test
    fun result_restore_is_exact_and_does_not_publish_completion_or_save() = runTest {
        val result = playableState(score = 812).copy(isGameOver = true, bestScore = 1_200)
        val deps = TestDependencies(bestScore = 1_500)
        val labels = mutableListOf<GameStore.Label>()
        val store = deps.factory.create(isNewGame = false, restoredResultState = result)
        backgroundScope.launch { store.labels.collect(labels::add) }
        runCurrent()

        assertEquals(result, store.state)
        assertEquals(0, deps.save.saveCount)
        assertTrue(labels.isEmpty())
        assertEquals(1, deps.audio.stopMusicCount)
    }

    @Test
    fun place_updates_only_store_state_and_logs_matching_fact() = runTest {
        val saved = playableState()
        val deps = TestDependencies(saved = saved)
        val store = deps.factory.create(isNewGame = false)
        runCurrent()
        val before = store.state

        store.accept(GameStore.Intent.Place(pieceId = 1, x = 2, y = 2))
        runCurrent()

        assertNotEquals(before, store.state)
        assertFalse(store.state.grid.isEmpty(2, 2))
        assertTrue(deps.analytics.has("piece_place_attempt"))
        assertTrue(deps.analytics.has("piece_place_success"))
    }

    @Test
    fun rejected_place_keeps_state_and_logs_failure() = runTest {
        val saved = playableState().copy(grid = Grid().withCell(2, 2, 4))
        val deps = TestDependencies(saved = saved)
        val store = deps.factory.create(isNewGame = false)
        runCurrent()
        val before = store.state

        store.accept(GameStore.Intent.Place(pieceId = 1, x = 2, y = 2))
        runCurrent()

        assertEquals(before, store.state)
        assertTrue(deps.analytics.has("piece_place_failed"))
    }

    @Test
    fun terminal_move_flushes_snapshot_before_publishing_completion() = runTest {
        val deps = TestDependencies(saved = stateOneMoveFromGameOver())
        val store = deps.factory.create(isNewGame = false)
        val labels = mutableListOf<GameStore.Label>()
        backgroundScope.launch { store.labels.collect(labels::add) }
        runCurrent()

        store.accept(GameStore.Intent.Place(pieceId = 1, x = 1, y = 0))
        runCurrent()

        val completed = labels.filterIsInstance<GameStore.Label.GameCompleted>().single()
        assertTrue(completed.finalState.isGameOver)
        assertEquals(completed.finalState, deps.save.stored)
        assertEquals(completed.finalState, store.state)
        assertTrue(deps.analytics.has("game_over"))
        assertTrue(deps.audio.stopMusicCount > 0)
    }

    @Test
    fun terminal_save_failure_still_publishes_completion_and_logs_failure() = runTest {
        val deps = TestDependencies(
            saved = stateOneMoveFromGameOver(),
            failSaves = true,
        )
        val store = deps.factory.create(isNewGame = false)
        val labels = mutableListOf<GameStore.Label>()
        backgroundScope.launch { store.labels.collect(labels::add) }
        runCurrent()

        store.accept(GameStore.Intent.Place(pieceId = 1, x = 1, y = 0))
        runCurrent()

        assertEquals(1, labels.filterIsInstance<GameStore.Label.GameCompleted>().size)
        assertTrue(
            deps.analytics.has(
                "game_persistence_failed",
                "operation" to "terminal_save",
            ),
        )
    }

    @Test
    fun qualifying_terminal_move_marks_and_persists_review_opportunity_without_global_policy() = runTest {
        val qualifyingScore =
            ReviewOpportunityConfig.MIN_SCORE + ReviewOpportunityConfig.BEST_SCORE_DELTA
        val saved = stateOneMoveFromGameOver().copy(
            score = qualifyingScore,
            bestScore = qualifyingScore,
            bestAtRoundStart = 0,
        )
        val deps = TestDependencies(
            saved = saved,
        )
        val store = deps.factory.create(isNewGame = false)
        val labels = mutableListOf<GameStore.Label>()
        backgroundScope.launch { store.labels.collect(labels::add) }
        runCurrent()

        store.accept(GameStore.Intent.Place(pieceId = 1, x = 1, y = 0))
        runCurrent()

        val completed = labels.filterIsInstance<GameStore.Label.GameCompleted>().single()
        assertTrue(completed.reviewOpportunity)
        assertTrue(completed.finalState.reviewPromptFiredThisRound)
        assertTrue(deps.save.stored?.reviewPromptFiredThisRound == true)
    }

    @Test
    fun revive_flushes_playable_state_before_publishing_success() = runTest {
        val terminal = playableState().copy(isGameOver = true)
        val deps = TestDependencies()
        val store = deps.factory.create(isNewGame = false, restoredResultState = terminal)
        val labels = mutableListOf<GameStore.Label>()
        backgroundScope.launch { store.labels.collect(labels::add) }
        runCurrent()

        store.accept(GameStore.Intent.Revive)
        runCurrent()

        val revived = labels.filterIsInstance<GameStore.Label.ReviveCompleted>().single()
        assertFalse(revived.playableState.isGameOver)
        assertEquals(1, revived.playableState.revivesUsed)
        assertEquals(revived.playableState, deps.save.stored)
        assertEquals(revived.playableState, store.state)
    }

    @Test
    fun revive_save_failure_restores_exact_terminal_state() = runTest {
        val terminal = playableState(score = 900).copy(
            grid = Grid().withCell(3, 4, 2),
            isGameOver = true,
        )
        val deps = TestDependencies(failSaves = true)
        val store = deps.factory.create(isNewGame = false, restoredResultState = terminal)
        val labels = mutableListOf<GameStore.Label>()
        backgroundScope.launch { store.labels.collect(labels::add) }
        runCurrent()

        store.accept(GameStore.Intent.Revive)
        runCurrent()

        assertEquals(terminal, store.state)
        assertEquals(1, labels.filterIsInstance<GameStore.Label.ReviveFailed>().size)
        assertTrue(labels.none { it is GameStore.Label.ReviveCompleted })
    }

    @Test
    fun restart_replaces_round_and_keeps_piece_ids_monotonic() = runTest {
        val saved = playableState().copy(nextPieceId = 10)
        val deps = TestDependencies(saved = saved)
        val store = deps.factory.create(isNewGame = false)
        runCurrent()

        store.accept(GameStore.Intent.Restart)
        runCurrent()

        assertEquals(0, store.state.score)
        assertTrue(store.state.currentPieces.all { it.pieceId > 10 })
        assertTrue(deps.analytics.has("game_started", "source" to "restart"))
    }

    @Test
    fun ordinary_move_is_saved_after_debounce() = runTest {
        val deps = TestDependencies(saved = playableState())
        val store = deps.factory.create(isNewGame = false)
        runCurrent()

        store.accept(GameStore.Intent.Place(pieceId = 1, x = 2, y = 2))
        advanceTimeBy(300)
        runCurrent()

        assertEquals(store.state, deps.save.stored)
    }

    private fun playableState(score: Long = 0): GameState = GameState(
        grid = Grid().withCell(7, 7, 5),
        score = score,
        bestScore = score,
        currentPieces = listOf(Piece(1, singleCell, 1)),
        nextPieceId = 1,
    )

    private fun stateOneMoveFromGameOver(): GameState {
        var grid = Grid()
        for (y in 0 until Grid.SIZE) for (x in 0 until Grid.SIZE) {
            if ((x + y) % 2 == 0) grid = grid.withCell(x, y, 3)
        }
        return GameState(
            grid = grid,
            currentPieces = listOf(
                Piece(1, singleCell, 1),
                Piece(2, horizontalTwo, 2),
            ),
            nextPieceId = 2,
        )
    }

    private inner class TestDependencies(
        saved: GameState? = null,
        bestScore: Long = 0,
        failSaves: Boolean = false,
    ) {
        val save = RecordingSaveRepository(saved, failSaves)
        val bestScoreRepository = RecordingBestScoreRepository(bestScore)
        val audio = RecordingAudioRepository()
        val tutorial = FakeTutorialRepository()
        val analytics = RecordingAnalyticsRepository()
        val factory = GameStoreFactory(
            storeFactory = DefaultStoreFactory(),
            gameReducer = GameSessionReducer(FixedShapeGenerator(), ScoreCalculator()),
            audio = audio,
            saveRepository = save,
            bestScoreRepository = bestScoreRepository,
            tutorialRepository = tutorial,
            analytics = analytics,
        )
    }

    private class FixedShapeGenerator : ShapeGenerator {
        override fun nextTray(seed: Long?): List<Polyomino> = List(3) { singleCell }
        override fun smallReviveTray(): List<Polyomino> = List(3) { singleCell }
    }

    private class RecordingSaveRepository(
        initial: GameState?,
        private val fail: Boolean,
    ) : GameSaveRepository {
        var stored: GameState? = initial
        var saveCount = 0
        override suspend fun save(state: GameState) {
            if (fail) error("save failed")
            stored = state
            saveCount += 1
        }
        override suspend fun load(): GameState? = stored
        override suspend fun clear() { stored = null }
    }

    private class RecordingBestScoreRepository(
        bestScore: Long,
    ) : BestScoreRepository {
        private val best = MutableStateFlow(bestScore)
        override val bestScore = best.asStateFlow()
        override suspend fun setBestScore(score: Long) {
            if (score > best.value) best.value = score
        }
    }

    private class FakeTutorialRepository : BlockBlastTutorialRepository {
        override val tutorialSeen = MutableStateFlow(false).asStateFlow()
        override suspend fun markSeen() = Unit
    }

    private class RecordingAudioRepository : AudioRepository {
        val sounds = mutableListOf<String>()
        val playlists = mutableListOf<List<String>>()
        var startMusicCount = 0
        var stopMusicCount = 0
        override suspend fun playSound(filename: String) { sounds += filename }
        override suspend fun startMusic(tracks: List<String>) {
            playlists += tracks
            startMusicCount += 1
        }
        override suspend fun stopMusic() { stopMusicCount += 1 }
        override suspend fun onAppBackground() = Unit
        override suspend fun onAppForeground() = Unit
    }

    private class RecordingAnalyticsRepository : AnalyticRepository {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun logEvent(eventName: String, params: Map<String, Any>?) {
            events += eventName to params.orEmpty()
        }
        override fun deleteData() = Unit
        fun has(name: String, vararg entries: Pair<String, Any>): Boolean =
            events.any { (event, params) ->
                event == name && entries.all { (key, value) -> params[key] == value }
            }
    }

    private companion object {
        val singleCell = Polyomino("single", listOf(Position(0, 0)))
        val horizontalTwo = Polyomino("horizontal_two", listOf(Position(0, 0), Position(1, 0)))
    }
}
