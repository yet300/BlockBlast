package ge.yet.game.blockblast.session

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.game.blockblast.component.game.DefaultGameComponent
import ge.yet.game.blockblast.component.game.GameComponent
import ge.yet.game.blockblast.component.game.store.GameInitializer
import ge.yet.game.blockblast.component.game.store.GameStoreFactory
import ge.yet.game.blockblast.data.audio.BlockBlastAudioPlayer
import ge.yet.game.blockblast.component.game.store.ReviewOpportunityConfig
import ge.yet.game.blockblast.component.result.BlockBlastResultSnapshot
import ge.yet.game.blockblast.component.result.DefaultGameResultComponentFactory
import ge.yet.game.blockblast.component.result.GameResultComponent
import ge.yet.game.blockblast.component.tray.PieceTrayComponent
import ge.yet.game.blockblast.component.tray.TraySelection
import ge.yet.game.blockblast.component.tray.TraySlotComponent
import ge.yet.game.blockblast.data.repository.SettingsBackedGameSaveRepository
import ge.yet.game.blockblast.data.repository.BlockBlastStorage
import ge.yet.game.blockblast.domain.engine.GameSessionReducer
import ge.yet.game.blockblast.domain.engine.ScoreCalculator
import ge.yet.game.blockblast.domain.engine.ShapeGenerator
import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.blockblast.domain.model.FeedbackType
import ge.yet.game.blockblast.domain.model.Grid
import ge.yet.game.blockblast.domain.model.Piece
import ge.yet.game.blockblast.domain.model.Polyomino
import ge.yet.game.blockblast.domain.model.Position
import ge.yet.game.blockblast.domain.repository.BestScoreRepository
import ge.yet.game.blockblast.domain.repository.BlockBlastTutorialRepository
import ge.yet.game.blockblast.domain.repository.GameSaveRepository
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.miniapp.api.MiniAppReviewOpportunity
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.compose.MiniAppFrameMode
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import ge.yet.game.miniapp.testkit.MutableMiniAppStorage
import ge.yet.game.miniapp.testkit.RecordingMiniAppSessionHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBlockBlastSessionComponentTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun play_uses_resume_mode_and_existing_save_initializer() = runTest(testDispatcher) {
        val saved = playableState(score = 321L)
        val saveRepository = settingsBackedSaveRepository()
        saveRepository.save(saved)
        val gameFactory = InitializingGameFactory(
            scope = this,
            initializer = gameInitializer(saveRepository),
        )

        val setup = build(gameFactory = gameFactory)
        runCurrent()

        assertEquals(listOf(false), gameFactory.requestedIsNewGame)
        assertEquals(GameInitializer.Source.Continue, gameFactory.initializations.single().source)
        assertEquals(saved, playing(setup).model.value.game)
        setup.destroy()
    }

    @Test
    fun play_without_a_save_falls_back_to_a_fresh_round() = runTest(testDispatcher) {
        val gameFactory = InitializingGameFactory(
            scope = this,
            initializer = gameInitializer(settingsBackedSaveRepository()),
        )

        val setup = build(gameFactory = gameFactory)
        runCurrent()

        assertEquals(listOf(false), gameFactory.requestedIsNewGame)
        assertEquals(GameInitializer.Source.New, gameFactory.initializations.single().source)
        assertFalse(playing(setup).model.value.game.isGameOver)
        assertTrue(playing(setup).model.value.game.currentPieces.isNotEmpty())
        setup.destroy()
    }

    @Test
    fun completion_adds_exactly_one_result() {
        val setup = build()
        val finalState = resultState()

        playing(setup).complete(finalState, canContinue = true)

        assertEquals(2, setup.component.stack.value.items.size)
        val result = result(setup)
        assertEquals(BlockBlastResultSnapshot.from(finalState), result.model.value.snapshot)
        assertTrue(result.model.value.canContinue)
        setup.destroy()
    }

    @Test
    fun frame_mode_follows_the_active_decompose_child() {
        val setup = build()

        assertEquals(MiniAppFrameMode.Standard, setup.component.frameMode.value)

        playing(setup).complete(resultState(), canContinue = true)
        assertEquals(MiniAppFrameMode.ContentOnly, setup.component.frameMode.value)

        fakeResult(setup).newGameRequested()
        assertEquals(MiniAppFrameMode.Standard, setup.component.frameMode.value)
        setup.destroy()
    }

    @Test
    fun duplicate_completion_does_not_repeat_result_or_review() {
        val setup = build()
        val game = playing(setup)
        val finalState = resultState()

        game.complete(finalState, canContinue = true, reviewOpportunity = true)
        game.complete(finalState, canContinue = true, reviewOpportunity = true)

        assertEquals(2, setup.component.stack.value.items.size)
        assertEquals(1, setup.resultFactory.created.size)
        assertEquals(1, setup.host.reviewRequests.size)
        setup.destroy()
    }

    @Test
    fun revive_success_returns_to_the_same_live_playing_child() {
        val setup = build()
        playing(setup).complete(resultState(), canContinue = true)
        val livePlaying = playing(setup)

        fakeResult(setup).continueRequested()

        assertEquals(1, setup.component.stack.value.items.size)
        assertSame(livePlaying, playing(setup))
        assertFalse(playing(setup).model.value.game.isGameOver)
        setup.destroy()
    }

    @Test
    fun revive_failure_keeps_the_matching_result() {
        val gameFactory = RecordingGameFactory(reviveSucceeds = false)
        val setup = build(gameFactory = gameFactory)
        playing(setup).complete(resultState(), canContinue = true)
        val result = fakeResult(setup)

        result.continueRequested()

        assertSame(result, result(setup))
        assertEquals(1, result.continueFailureCount)
        assertEquals(2, setup.component.stack.value.items.size)
        setup.destroy()
    }

    @Test
    fun stale_revive_callback_cannot_mutate_a_newer_round() {
        val setup = build()
        playing(setup).complete(resultState(), canContinue = true)
        val staleGame = playing(setup)
        fakeResult(setup).newGameRequested()
        playing(setup).complete(resultState().copy(score = 999L), canContinue = true)
        val newerResult = fakeResult(setup)

        staleGame.succeedRevive(resultState().copy(isGameOver = false))

        assertSame(newerResult, fakeResult(setup))
        assertEquals(0, newerResult.continueFailureCount)
        assertEquals(2, setup.component.stack.value.items.size)

        staleGame.failRevive()

        assertSame(newerResult, fakeResult(setup))
        assertEquals(0, newerResult.continueFailureCount)
        assertEquals(2, setup.component.stack.value.items.size)
        setup.destroy()
    }

    @Test
    fun stale_result_callbacks_cannot_mutate_a_newer_result_for_the_same_round() {
        val setup = build()
        val game = playing(setup)
        game.complete(resultState(), canContinue = true)
        val staleResult = fakeResult(setup)
        staleResult.continueRequested()
        game.complete(resultState().copy(score = 999L), canContinue = true)
        val newerResult = fakeResult(setup)
        val newerModel = newerResult.model.value

        staleResult.continueRequested()

        assertSame(newerResult, fakeResult(setup))
        assertEquals(newerModel, newerResult.model.value)
        assertEquals(0, newerResult.continueFailureCount)
        assertEquals(2, setup.component.stack.value.items.size)

        staleResult.newGameRequested()

        assertSame(newerResult, fakeResult(setup))
        assertEquals(newerModel, newerResult.model.value)
        assertEquals(0, newerResult.continueFailureCount)
        assertEquals(2, setup.component.stack.value.items.size)
        setup.destroy()
    }

    @Test
    fun stale_completion_cannot_replace_a_newer_playing_round_or_request_review() {
        val setup = build()
        val staleGame = playing(setup)
        staleGame.complete(resultState(), canContinue = true)
        fakeResult(setup).newGameRequested()
        val newerPlaying = playing(setup)

        staleGame.complete(
            finalState = resultState().copy(score = 111L),
            canContinue = true,
            reviewOpportunity = true,
        )

        assertEquals(1, setup.component.stack.value.items.size)
        assertSame(newerPlaying, playing(setup))
        assertTrue(newerPlaying.isNewGame)
        assertTrue(setup.host.reviewRequests.isEmpty())
        setup.destroy()
    }

    @Test
    fun new_game_replaces_the_finished_internal_stack() {
        val setup = build()
        val finishedGame = playing(setup)
        finishedGame.complete(resultState(), canContinue = true)

        fakeResult(setup).newGameRequested()

        assertEquals(1, setup.component.stack.value.items.size)
        assertNotSame(finishedGame, playing(setup))
        assertTrue(playing(setup).isNewGame)
        assertEquals(null, playing(setup).restoredResultState)
        assertEquals(0, setup.host.closeCount)
        setup.destroy()
    }

    @Test
    fun qualified_review_contains_typed_score_facts_once() {
        val setup = build()
        val finalState = resultState().copy(
            score = ReviewOpportunityConfig.MIN_SCORE + 100L,
            bestScore = ReviewOpportunityConfig.MIN_SCORE + 150L,
            revivesUsed = 1,
        )
        val game = playing(setup)

        game.complete(finalState, canContinue = true, reviewOpportunity = true)
        game.complete(finalState, canContinue = true, reviewOpportunity = true)

        assertEquals(
            listOf(
                MiniAppReviewOpportunity(
                    triggerId = "block_blast_result",
                    score = finalState.score,
                    bestScore = finalState.bestScore,
                    revivesUsed = finalState.revivesUsed,
                ),
            ),
            setup.host.reviewRequests,
        )
        setup.destroy()
    }

    @Test
    fun obscured_or_background_visibility_rejects_game_input() = runTest(testDispatcher) {
        val visibility = MutableMiniAppVisibilitySource(MiniAppVisibility.OBSCURED)
        val lifecycle = LifecycleRegistry()
        val component = realGameComponent(
            lifecycle = lifecycle,
            visibility = visibility,
            restoredResultState = playableState(score = 10L),
        )
        runCurrent()
        val before = component.model.value.game
        val piece = before.currentPieces.first()

        component.onCellClicked(piece.pieceId, 0, 0)
        runCurrent()
        assertEquals(before, component.model.value.game)

        visibility.set(MiniAppVisibility.BACKGROUND)
        component.onCellClicked(piece.pieceId, 0, 0)
        runCurrent()
        assertEquals(before, component.model.value.game)

        visibility.set(MiniAppVisibility.ACTIVE)
        component.onCellClicked(piece.pieceId, 0, 0)
        runCurrent()
        assertTrue(component.model.value.game.score > before.score)
        lifecycle.destroy()
    }

    @Test
    fun result_countdown_pauses_until_visibility_is_active() = runTest(testDispatcher) {
        val visibility = MutableMiniAppVisibilitySource()
        val setup = build(
            visibility = visibility,
            resultFactory = DefaultGameResultComponentFactory(visibility),
        )
        playing(setup).complete(resultState(), canContinue = true)
        val result = result(setup)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(4, result.model.value.continueSecondsRemaining)

        visibility.set(MiniAppVisibility.OBSCURED)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(4, result.model.value.continueSecondsRemaining)

        visibility.set(MiniAppVisibility.BACKGROUND)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(4, result.model.value.continueSecondsRemaining)

        visibility.set(MiniAppVisibility.ACTIVE)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(3, result.model.value.continueSecondsRemaining)
        setup.destroy()
    }

    private fun build(
        gameFactory: GameComponent.Factory = RecordingGameFactory(),
        resultFactory: GameResultComponent.Factory = RecordingResultFactory(),
        visibility: MutableMiniAppVisibilitySource = MutableMiniAppVisibilitySource(),
    ): Setup {
        val lifecycle = LifecycleRegistry()
        val host = RecordingMiniAppSessionHost()
        val recordingResultFactory = resultFactory as? RecordingResultFactory
        val component = DefaultBlockBlastSessionComponent(
            componentContext = DefaultComponentContext(lifecycle),
            gameFactory = gameFactory,
            resultFactory = resultFactory,
            visibility = visibility,
            host = host,
        )
        lifecycle.resume()
        return Setup(
            component = component,
            lifecycle = lifecycle,
            host = host,
            resultFactory = recordingResultFactory ?: RecordingResultFactory(),
        )
    }

    private fun playing(setup: Setup): FakeGame =
        assertIs<BlockBlastSessionComponent.Child.Playing>(
            setup.component.stack.value.items.first().instance,
        ).component as FakeGame

    private fun result(setup: Setup): GameResultComponent =
        assertIs<BlockBlastSessionComponent.Child.Result>(
            setup.component.stack.value.active.instance,
        ).component

    private fun fakeResult(setup: Setup): FakeResult = result(setup) as FakeResult

    private fun settingsBackedSaveRepository(): SettingsBackedGameSaveRepository =
        SettingsBackedGameSaveRepository(BlockBlastStorage(MutableMiniAppStorage()))

    private fun gameInitializer(saveRepository: GameSaveRepository): GameInitializer =
        GameInitializer(
            gameReducer = GameSessionReducer(OneByOneGenerator(), ScoreCalculator()),
            saveRepository = saveRepository,
            bestScoreRepository = FakeBestScoreRepository(),
            tutorialRepository = FakeTutorialRepository(),
        )

    private fun realGameComponent(
        lifecycle: LifecycleRegistry,
        visibility: MutableMiniAppVisibilitySource,
        restoredResultState: GameState,
    ): DefaultGameComponent {
        val reducer = GameSessionReducer(OneByOneGenerator(), ScoreCalculator())
        val audio = RecordingAudio()
        val tutorial = FakeTutorialRepository()
        val storeFactory = GameStoreFactory(
            storeFactory = DefaultStoreFactory(),
            gameReducer = reducer,
            audio = audio,
            saveRepository = InMemoryGameSaveRepository(),
            bestScoreRepository = FakeBestScoreRepository(),
            tutorialRepository = tutorial,
            analytics = RecordingAnalytics(),
        )
        return DefaultGameComponent(
            componentContext = DefaultComponentContext(lifecycle),
            analytics = RecordingAnalytics(),
            gameStoreFactory = storeFactory,
            audio = audio,
            tutorialRepository = tutorial,
            visibility = visibility,
            isNewGame = false,
            restoredResultState = restoredResultState,
            onGameCompletedCb = { _, _, _ -> },
            onReviveCompletedCb = {},
            onReviveFailedCb = {},
        )
    }

    private data class Setup(
        val component: DefaultBlockBlastSessionComponent,
        val lifecycle: LifecycleRegistry,
        val host: RecordingMiniAppSessionHost,
        val resultFactory: RecordingResultFactory,
    ) {
        fun destroy() = lifecycle.destroy()
    }

    private open class RecordingGameFactory(
        private val reviveSucceeds: Boolean = true,
    ) : GameComponent.Factory {
        val requestedIsNewGame = mutableListOf<Boolean>()
        val created = mutableListOf<FakeGame>()

        override fun create(
            componentContext: ComponentContext,
            isNewGame: Boolean,
            restoredResultState: GameState?,
            onGameCompleted: (GameState, Boolean, Boolean) -> Unit,
            onReviveCompleted: (GameState) -> Unit,
            onReviveFailed: () -> Unit,
        ): GameComponent {
            requestedIsNewGame += isNewGame
            return FakeGame(
                isNewGame = isNewGame,
                restoredResultState = restoredResultState,
                reviveSucceeds = reviveSucceeds,
                onGameCompleted = onGameCompleted,
                onReviveCompleted = onReviveCompleted,
                onReviveFailed = onReviveFailed,
            ).also(created::add)
        }
    }

    private class InitializingGameFactory(
        private val scope: TestScope,
        private val initializer: GameInitializer,
    ) : GameComponent.Factory {
        val requestedIsNewGame = mutableListOf<Boolean>()
        val initializations = mutableListOf<GameInitializer.Result>()

        override fun create(
            componentContext: ComponentContext,
            isNewGame: Boolean,
            restoredResultState: GameState?,
            onGameCompleted: (GameState, Boolean, Boolean) -> Unit,
            onReviveCompleted: (GameState) -> Unit,
            onReviveFailed: () -> Unit,
        ): GameComponent {
            requestedIsNewGame += isNewGame
            return FakeGame(
                isNewGame = isNewGame,
                restoredResultState = restoredResultState,
                reviveSucceeds = true,
                onGameCompleted = onGameCompleted,
                onReviveCompleted = onReviveCompleted,
                onReviveFailed = onReviveFailed,
            ).also { game ->
                scope.launch {
                    val initialization = initializer.initialize(
                        isNewGame = isNewGame,
                        restoredResultState = restoredResultState,
                    )
                    initializations += initialization
                    game.setState(initialization.state)
                }
            }
        }
    }

    private class FakeGame(
        val isNewGame: Boolean,
        val restoredResultState: GameState?,
        private val reviveSucceeds: Boolean,
        private val onGameCompleted: (GameState, Boolean, Boolean) -> Unit,
        private val onReviveCompleted: (GameState) -> Unit,
        private val onReviveFailed: () -> Unit,
    ) : GameComponent {
        private val playablePiece = Piece(
            pieceId = 1L,
            shape = Polyomino("1x1", listOf(Position(0, 0))),
            colorId = 0,
        )
        private var gameState = restoredResultState ?: GameState(currentPieces = listOf(playablePiece))

        override val model = MutableValue(GameComponent.Model(gameState))
        override val tutorialSeen = MutableStateFlow(true).asStateFlow()
        override val pieceTray = object : PieceTrayComponent {
            override val slots = MutableValue(emptyList<TraySlotComponent>())
            override val selection = MutableValue(TraySelection.NONE)
            override fun clearSelection() = Unit
        }

        override fun onCellClicked(pieceId: Long, x: Int, y: Int) = Unit

        override fun onReviveClicked() {
            if (!reviveSucceeds) {
                onReviveFailed()
                return
            }
            val playable = gameState.copy(
                currentPieces = gameState.currentPieces.ifEmpty { listOf(playablePiece) },
                isGameOver = false,
                revivesUsed = gameState.revivesUsed + 1,
            )
            setState(playable)
            onReviveCompleted(playable)
        }

        override fun onTutorialSeen() = Unit

        fun setState(state: GameState) {
            gameState = state
            model.value = GameComponent.Model(state)
        }

        fun complete(
            finalState: GameState,
            canContinue: Boolean,
            reviewOpportunity: Boolean = false,
        ) = onGameCompleted(finalState, canContinue, reviewOpportunity)

        fun succeedRevive(playableState: GameState) = onReviveCompleted(playableState)

        fun failRevive() = onReviveFailed()
    }

    private class RecordingResultFactory : GameResultComponent.Factory {
        val created = mutableListOf<FakeResult>()

        override fun create(
            componentContext: ComponentContext,
            snapshot: BlockBlastResultSnapshot,
            canContinue: Boolean,
            onContinueRequested: () -> Unit,
            onNewGameRequested: () -> Unit,
        ): GameResultComponent = FakeResult(
            snapshot = snapshot,
            canContinue = canContinue,
            continueRequested = onContinueRequested,
            newGameRequested = onNewGameRequested,
        ).also(created::add)
    }

    private class FakeResult(
        snapshot: BlockBlastResultSnapshot,
        canContinue: Boolean,
        val continueRequested: () -> Unit,
        val newGameRequested: () -> Unit,
    ) : GameResultComponent {
        var continueFailureCount = 0
        override val model = MutableValue(
            GameResultComponent.Model(
                snapshot = snapshot,
                canContinue = canContinue,
                continueSecondsRemaining = if (canContinue) 5 else 0,
            ),
        )

        override fun onPrimaryClicked(requestContinue: (onApproved: () -> Unit) -> Unit) =
            continueRequested()

        override fun onContinueFailed() {
            continueFailureCount += 1
        }
    }

    private class OneByOneGenerator : ShapeGenerator {
        private val one = Polyomino("1x1", listOf(Position(0, 0)))
        override fun nextTray(seed: Long?): List<Polyomino> = listOf(one, one, one)
        override fun smallReviveTray(): List<Polyomino> = listOf(one, one, one)
    }

    private class FakeBestScoreRepository : BestScoreRepository {
        private val score = MutableStateFlow(0L)
        override val bestScore: StateFlow<Long> = score.asStateFlow()
        override suspend fun setBestScore(score: Long) {
            if (score > this.score.value) this.score.value = score
        }
    }

    private class FakeTutorialRepository : BlockBlastTutorialRepository {
        private val seen = MutableStateFlow(true)
        override val tutorialSeen: StateFlow<Boolean> = seen.asStateFlow()
        override suspend fun markSeen() {
            seen.value = true
        }
    }

    private class InMemoryGameSaveRepository : GameSaveRepository {
        private var state: GameState? = null
        override suspend fun save(state: GameState) {
            this.state = state
        }
        override suspend fun load(): GameState? = state
        override suspend fun clear() {
            state = null
        }
    }

    private class RecordingAudio : BlockBlastAudioPlayer {
        override fun playFeedback(type: FeedbackType) = Unit
        override fun startMusic() = Unit
        override fun stopMusic() = Unit
    }

    private class RecordingAnalytics : AnalyticRepository {
        override fun logEvent(eventName: String, params: Map<String, Any>?) = Unit
        override fun deleteData() = Unit
    }

    private fun playableState(score: Long): GameState = GameState(
        grid = Grid(),
        score = score,
        bestScore = score,
        currentPieces = listOf(
            Piece(
                pieceId = 1L,
                shape = Polyomino("1x1", listOf(Position(0, 0))),
                colorId = 1,
            ),
        ),
        nextPieceId = 2L,
    )

    private fun resultState(): GameState = playableState(score = 400L).copy(
        bestScore = 500L,
        isGameOver = true,
        revivesUsed = 0,
    )
}
