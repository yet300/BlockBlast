package ge.yet.game.blockblast.component.game.store

import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.game.blockblast.domain.engine.GameSessionReducer
import ge.yet.game.blockblast.domain.engine.ScoreCalculator
import ge.yet.game.blockblast.domain.engine.ShapeGenerator
import ge.yet.game.blockblast.data.audio.BlockBlastAudioPlayer
import ge.yet.game.blockblast.domain.model.FeedbackType
import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.blockblast.domain.model.Polyomino
import ge.yet.game.blockblast.domain.model.Position
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.blockblast.domain.repository.GameSaveRepository
import ge.yet.game.blockblast.domain.repository.BlockBlastTutorialRepository
import ge.yet.game.blockblast.domain.repository.BestScoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GameStoreOwnershipTest {
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
    fun stores_created_by_one_factory_own_independent_game_states() = runTest {
        val generator = SingleCellGenerator()
        val repository = MemorySaveRepository()
        val factory = GameStoreFactory(
            storeFactory = DefaultStoreFactory(),
            gameReducer = GameSessionReducer(generator, ScoreCalculator()),
            audio = SilentAudioRepository(),
            saveRepository = repository,
            bestScoreRepository = MemoryBestScoreRepository(),
            tutorialRepository = MemoryTutorialRepository(),
            analytics = SilentAnalyticsRepository(),
        )
        val first = factory.create(isNewGame = true, newGameSeed = 7)
        val second = factory.create(isNewGame = true, newGameSeed = 7)
        runCurrent()
        val secondBeforeMove = second.state
        val piece = first.state.currentPieces.first()

        first.accept(GameStore.Intent.Place(piece.pieceId, x = 0, y = 0))
        runCurrent()

        assertNotEquals(secondBeforeMove, first.state)
        assertEquals(secondBeforeMove, second.state)
    }

    private class SingleCellGenerator : ShapeGenerator {
        private val shape = Polyomino("single", listOf(Position(0, 0)))
        override fun nextTray(seed: Long?): List<Polyomino> = List(3) { shape }
        override fun smallReviveTray(): List<Polyomino> = List(3) { shape }
    }

    private class MemorySaveRepository : GameSaveRepository {
        private var state: GameState? = null
        override suspend fun save(state: GameState) { this.state = state }
        override suspend fun load(): GameState? = state
        override suspend fun clear() { state = null }
    }

    private class MemoryBestScoreRepository : BestScoreRepository {
        override val bestScore = MutableStateFlow(0L).asStateFlow()
        override suspend fun setBestScore(score: Long) = Unit
    }

    private class MemoryTutorialRepository : BlockBlastTutorialRepository {
        override val tutorialSeen = MutableStateFlow(false).asStateFlow()
        override suspend fun markSeen() = Unit
    }

    private class SilentAudioRepository : BlockBlastAudioPlayer {
        override fun playFeedback(type: FeedbackType) = Unit
        override fun startMusic() = Unit
        override fun stopMusic() = Unit
    }

    private class SilentAnalyticsRepository : AnalyticRepository {
        override fun logEvent(eventName: String, params: Map<String, Any>?) = Unit
        override fun deleteData() = Unit
    }
}
