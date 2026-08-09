package ge.yet.blockblast.feature.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.arkivanov.essenty.statekeeper.StateKeeper
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import ge.yet.blockblast.feature.game.GameComponent
import ge.yet.blockblast.feature.game.result.BlockBlastResultSnapshot
import ge.yet.blockblast.feature.game.result.GameResultComponent
import ge.yet.blockblast.feature.game.reviewprompt.ReviewPromptComponent
import ge.yet.blockblast.feature.home.HomeComponent
import ge.yet.blokblast.domain.engine.GameEngine
import ge.yet.blokblast.domain.engine.ScoreCalculator
import ge.yet.blokblast.domain.engine.ShapeGenerator
import ge.yet.blokblast.domain.model.FeedbackType
import ge.yet.blokblast.domain.model.GameState
import ge.yet.blokblast.domain.model.Grid
import ge.yet.blokblast.domain.model.Polyomino
import ge.yet.blokblast.domain.model.Position
import ge.yet.blokblast.domain.repository.AudioRepository
import ge.yet.blokblast.domain.repository.GameSaveRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultRootComponentTest {

    private fun build(
        stateKeeper: StateKeeper? = null,
        gameFactory: RecordingGameFactory = RecordingGameFactory(),
        resultFactory: RecordingResultFactory = RecordingResultFactory(),
    ): Setup {
        val lifecycle = LifecycleRegistry()
        val audio = RecordingAudio()
        val settings = FakeSettings()
        val homeFactory = RecordingHomeFactory()
        val component = DefaultRootComponent(
            componentContext = DefaultComponentContext(
                lifecycle = lifecycle,
                stateKeeper = stateKeeper,
            ),
            homeFactory = homeFactory,
            gameFactory = gameFactory,
            resultFactory = resultFactory,
            audio = audio,
            settingsRepository = settings,
        )
        return Setup(component, lifecycle, audio, settings, homeFactory, gameFactory, resultFactory)
    }

    @Test
    fun initial_stack_is_home() {
        val (component, _, _, _, _) = build().destructure()
        assertIs<RootComponent.Child.Home>(component.stack.value.active.instance)
    }

    @Test
    fun settings_flows_are_exposed_by_root_component() {
        val (component, _, _, settings, _, _) = build()
        assertFalse(component.darkTheme.value)
        settings.darkFlow.value = true
        assertTrue(component.darkTheme.value)
        settings.sfxFlow.value = false
        assertFalse(component.sfxEnabled.value)
        settings.vibrationFlow.value = false
        assertFalse(component.vibrationEnabled.value)
        settings.tutorialFlow.value = true
        assertTrue(component.tutorialSeen.value)
        settings.adsFlow.value = false
        assertFalse(component.adsEnabled.value)
    }

    @Test
    fun onTutorialSeen_persists_via_repository() = runTest {
        val (component, _, _, settings, _, _) = build()
        component.onTutorialSeen()
        assertTrue(settings.tutorialFlow.value)
    }

    @Test
    fun resume_lifecycle_calls_audio_onAppForeground() = runTest {
        val (_, lifecycle, audio, _, _, _) = build()
        lifecycle.resume()
        assertTrue(audio.foregroundCount >= 1)
    }

    @Test
    fun stop_lifecycle_calls_audio_onAppBackground() = runTest {
        val (_, lifecycle, audio, _, _, _) = build()
        lifecycle.resume()
        lifecycle.stop()
        assertTrue(audio.backgroundCount >= 1)
    }

    @Test
    fun home_continueClicked_navigates_to_game_with_isNewGame_false() {
        val (component, _, _, _, homeFactory, gameFactory) = build()
        homeFactory.created.first().onContinueClicked(false)
        val child = component.stack.value.active.instance
        assertIs<RootComponent.Child.Game>(child)
        assertEquals(listOf(false), gameFactory.requestedIsNewGame)
    }

    @Test
    fun home_newGameClicked_navigates_to_game_with_isNewGame_true() {
        val (component, _, _, _, homeFactory, gameFactory) = build()
        homeFactory.created.first().onNewGameClicked(true)
        val child = component.stack.value.active.instance
        assertIs<RootComponent.Child.Game>(child)
        assertEquals(listOf(true), gameFactory.requestedIsNewGame)
    }

    @Test
    fun onBackClicked_pops_back_to_home() {
        val (component, _, _, _, homeFactory, _) = build()
        homeFactory.created.first().onNewGameClicked(true)
        assertIs<RootComponent.Child.Game>(component.stack.value.active.instance)
        component.onBackClicked()
        assertIs<RootComponent.Child.Home>(component.stack.value.active.instance)
    }

    @Test
    fun game_completion_pushes_result_with_final_snapshot() {
        val setup = build()
        setup.homeFactory.created.first().onNewGameClicked(true)
        val finalState = resultState()
        setup.gameFactory.created.single().complete(
            finalState,
            canContinue = true,
            shouldRequestReview = true,
        )
        assertIs<RootComponent.Child.Result>(setup.component.stack.value.active.instance)
        assertEquals(BlockBlastResultSnapshot.from(finalState), setup.resultFactory.created.single().snapshot)
        assertEquals(finalState, setup.gameFactory.created.last().restoredResultState)
        assertTrue(setup.resultFactory.created.single().canContinue)
        assertTrue(setup.resultFactory.created.single().shouldRequestReview)
    }

    @Test
    fun back_dismisses_review_prompt_before_leaving_result() {
        val setup = build()
        setup.homeFactory.created.first().onNewGameClicked(true)
        setup.gameFactory.created.single().complete(
            resultState(),
            canContinue = true,
            shouldRequestReview = true,
        )
        val result = setup.resultFactory.created.single()
        result.model.value = result.model.value.copy(continueSecondsRemaining = 3)

        setup.component.onBackClicked()

        assertIs<RootComponent.Child.Result>(setup.component.stack.value.active.instance)
        assertEquals(1, setup.resultFactory.created.size)
        assertEquals(3, result.model.value.continueSecondsRemaining)
        assertEquals(null, result.reviewPrompt.value.component)
        setup.component.onBackClicked()
        assertIs<RootComponent.Child.Home>(setup.component.stack.value.active.instance)
    }

    @Test
    fun duplicate_game_completion_does_not_push_duplicate_result() {
        val setup = build()
        setup.homeFactory.created.first().onNewGameClicked(true)
        val game = setup.gameFactory.created.single()
        val finalState = resultState()
        game.complete(finalState, canContinue = true)
        game.complete(finalState, canContinue = true)
        assertEquals(3, setup.component.stack.value.items.size)
        assertIs<RootComponent.Child.Result>(setup.component.stack.value.active.instance)
    }

    @Test
    fun result_continue_revives_live_game_and_pops_to_it() {
        val setup = build()
        setup.homeFactory.created.first().onNewGameClicked(true)
        setup.gameFactory.created.single().complete(resultState(), canContinue = true)
        val restoredGame = setup.gameFactory.created.last()
        setup.resultFactory.created.single().continueRequested()
        assertEquals(1, restoredGame.reviveCalls)
        assertIs<RootComponent.Child.Game>(setup.component.stack.value.active.instance)
    }

    @Test
    fun failed_result_continue_keeps_result_visible() {
        val gameFactory = RecordingGameFactory(failRevive = true)
        val setup = build(gameFactory = gameFactory)
        setup.homeFactory.created.first().onNewGameClicked(true)
        setup.gameFactory.created.single().complete(resultState(), canContinue = true)
        val restoredGame = setup.gameFactory.created.last()
        setup.resultFactory.created.single().continueRequested()
        assertEquals(1, restoredGame.reviveCalls)
        assertIs<RootComponent.Child.Result>(setup.component.stack.value.active.instance)
        assertEquals(1, setup.resultFactory.created.single().continueFailureCount)
        setup.resultFactory.created.single().homeRequested()
        assertIs<RootComponent.Child.Home>(setup.component.stack.value.active.instance)
    }

    @Test
    fun stale_game_failure_cannot_unlock_newer_result() {
        val setup = build()
        setup.homeFactory.created.first().onNewGameClicked(true)
        val oldGame = setup.gameFactory.created.single()
        oldGame.complete(resultState(), canContinue = true)
        setup.resultFactory.created.single().newGameRequested()
        val newGame = setup.gameFactory.created.last()
        newGame.complete(resultState().copy(score = 999L), canContinue = true)
        val newerResult = setup.resultFactory.created.last()

        oldGame.failRevive()

        assertEquals(0, newerResult.continueFailureCount)
    }

    @Test
    fun result_new_game_replaces_finished_flow_with_fresh_game() {
        val setup = build()
        setup.homeFactory.created.first().onNewGameClicked(true)
        setup.gameFactory.created.single().complete(resultState(), canContinue = true)
        setup.resultFactory.created.single().newGameRequested()
        assertTrue(setup.gameFactory.requestedIsNewGame.last())
        assertEquals(2, setup.component.stack.value.items.size)
        assertIs<RootComponent.Child.Game>(setup.component.stack.value.active.instance)
    }

    @Test
    fun result_home_destroys_finished_game_and_returns_home() {
        val setup = build()
        setup.homeFactory.created.first().onNewGameClicked(true)
        setup.gameFactory.created.single().complete(resultState(), canContinue = true)
        setup.resultFactory.created.single().homeRequested()
        assertEquals(1, setup.component.stack.value.items.size)
        assertIs<RootComponent.Child.Home>(setup.component.stack.value.active.instance)
    }

    @Test
    fun back_from_result_returns_home_without_revealing_dead_game() {
        val setup = build()
        setup.homeFactory.created.first().onNewGameClicked(true)
        setup.gameFactory.created.single().complete(resultState(), canContinue = true)
        setup.component.onBackClicked()
        assertEquals(1, setup.component.stack.value.items.size)
        assertIs<RootComponent.Child.Home>(setup.component.stack.value.active.instance)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun stateKeeper_after_continue_restores_durably_saved_playable_game() = runTest {
        val finalState = resultState()
        val firstStateKeeper = StateKeeperDispatcher()
        val firstRepository = RecordingGameSaveRepository()
        val first = build(
            stateKeeper = firstStateKeeper,
            gameFactory = RecordingGameFactory(
                saveRepository = firstRepository,
                externalScope = this,
            ),
        )
        first.homeFactory.created.first().onNewGameClicked(true)
        first.gameFactory.created.single().complete(finalState, canContinue = true)
        val savedNavigation = firstStateKeeper.save()
        first.lifecycle.destroy()

        val restoredRepository = RecordingGameSaveRepository()
        val restoredGameFactory = RecordingGameFactory(
            saveRepository = restoredRepository,
            externalScope = this,
        )
        val continuedStateKeeper = StateKeeperDispatcher(savedNavigation)
        val restored = build(
            stateKeeper = continuedStateKeeper,
            gameFactory = restoredGameFactory,
        )

        assertIs<RootComponent.Child.Result>(restored.component.stack.value.active.instance)
        val restoredGame = restoredGameFactory.created.single()
        assertEquals(finalState.score, restoredGame.engine.state.value.score)
        assertEquals(finalState.grid, restoredGame.engine.state.value.grid)
        assertTrue(restoredGame.engine.state.value.isGameOver)

        restored.resultFactory.created.single().continueRequested()
        runCurrent()

        assertIs<RootComponent.Child.Game>(restored.component.stack.value.active.instance)
        val playableState = restoredRepository.saved
        requireNotNull(playableState)
        assertEquals(finalState.score, playableState.score)
        assertEquals(finalState.grid, playableState.grid)
        assertFalse(playableState.isGameOver)
        assertEquals(finalState.revivesUsed + 1, playableState.revivesUsed)

        val normalizedNavigation = restored.component.stack.value
        assertEquals(2, normalizedNavigation.items.size)
        val savedAfterContinue = continuedStateKeeper.save()
        restored.lifecycle.destroy()

        val freshRepository = RecordingGameSaveRepository(initial = playableState)
        val freshFactory = RecordingGameFactory(
            saveRepository = freshRepository,
            externalScope = this,
        )
        val fresh = build(
            stateKeeper = StateKeeperDispatcher(savedAfterContinue),
            gameFactory = freshFactory,
        )
        assertIs<RootComponent.Child.Game>(fresh.component.stack.value.active.instance)
        val freshGame = freshFactory.created.single()
        assertEquals(playableState.score, freshGame.engine.state.value.score)
        assertEquals(playableState.grid, freshGame.engine.state.value.grid)
        assertEquals(playableState.revivesUsed, freshGame.engine.state.value.revivesUsed)
        assertFalse(freshGame.engine.state.value.isGameOver)

        val saveCountBeforePlacement = freshRepository.saveCount
        val piece = freshGame.engine.state.value.currentPieces.first()
        freshGame.onCellClicked(piece.pieceId, 0, 0)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(saveCountBeforePlacement + 1, freshRepository.saveCount)
    }

    private fun resultState(): GameState =
        GameState(
            grid = Grid().withCell(3, 4, 5),
            score = 42L,
            bestScore = 100L,
            bestAtRoundStart = 12L,
            isGameOver = true,
        )

    private fun Setup.destructure() = this

    private data class Setup(
        val component: DefaultRootComponent,
        val lifecycle: LifecycleRegistry,
        val audio: RecordingAudio,
        val settings: FakeSettings,
        val homeFactory: RecordingHomeFactory,
        val gameFactory: RecordingGameFactory,
        val resultFactory: RecordingResultFactory,
    )

    // ── Fakes ────────────────────────────────────────────────────────────

    private class RecordingHomeFactory : HomeComponent.Factory {
        val created = mutableListOf<FakeHome>()
        override fun create(
            componentContext: ComponentContext,
            onContinueClicked: (Boolean) -> Unit,
            onNewGameClicked: (Boolean) -> Unit,
        ): HomeComponent = FakeHome(onContinueClicked, onNewGameClicked).also { created += it }
    }

    private class FakeHome(
        val onContinueClicked: (Boolean) -> Unit,
        val onNewGameClicked: (Boolean) -> Unit,
    ) : HomeComponent {
        override val model = com.arkivanov.decompose.value.MutableValue(
            HomeComponent.Model(bestScore = 0L, hasSavedGame = false),
        )
        override fun onContinueClicked() = onContinueClicked(false)
        override fun onNewGameClicked() = onNewGameClicked(true)
    }

    private class RecordingGameFactory(
        private val failRevive: Boolean = false,
        private val saveRepository: RecordingGameSaveRepository = RecordingGameSaveRepository(),
        private val externalScope: CoroutineScope =
            CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
    ) : GameComponent.Factory {
        val requestedIsNewGame = mutableListOf<Boolean>()
        val created = mutableListOf<FakeGame>()
        override fun create(
            componentContext: ComponentContext,
            isNewGame: Boolean,
            restoredResultState: GameState?,
            onExitClicked: () -> Unit,
            onGameCompleted: (GameState, Boolean, Boolean) -> Unit,
            onReviveCompleted: (GameState) -> Unit,
            onReviveFailed: () -> Unit,
        ): GameComponent {
            requestedIsNewGame += isNewGame
            return FakeGame(
                restoredResultState = restoredResultState,
                isNewGame = isNewGame,
                failRevive = failRevive,
                saveRepository = saveRepository,
                externalScope = externalScope,
                onGameCompleted = onGameCompleted,
                onReviveCompleted = onReviveCompleted,
                onReviveFailed = onReviveFailed,
            ).also { created += it }
        }
    }

    private class FakeGame(
        val restoredResultState: GameState?,
        isNewGame: Boolean,
        private val failRevive: Boolean,
        private val saveRepository: RecordingGameSaveRepository,
        private val externalScope: CoroutineScope,
        private val onGameCompleted: (GameState, Boolean, Boolean) -> Unit,
        private val onReviveCompleted: (GameState) -> Unit,
        private val onReviveFailed: () -> Unit,
    ) : GameComponent {
        var reviveCalls = 0
        val engine = GameEngine(
            shapeGenerator = OneByOneGenerator(),
            scoreCalculator = ScoreCalculator(),
            saveRepository = saveRepository,
            externalScope = externalScope,
        )

        init {
            if (restoredResultState != null) {
                engine.restoreResult(restoredResultState)
            } else if (!isNewGame && saveRepository.saved != null) {
                engine.restore(checkNotNull(saveRepository.saved))
            } else {
                engine.startNewGame(bestScore = 0L)
            }
        }

        override val model = com.arkivanov.decompose.value.MutableValue(
            GameComponent.Model(game = engine.state.value),
        )
        override val sheetSlot = com.arkivanov.decompose.value.MutableValue(
            com.arkivanov.decompose.router.slot.ChildSlot<Any, GameComponent.SheetChild>(child = null),
        )
        override val pieceTray: ge.yet.blockblast.feature.game.tray.PieceTrayComponent =
            object : ge.yet.blockblast.feature.game.tray.PieceTrayComponent {
                override val slots = com.arkivanov.decompose.value.MutableValue(
                    emptyList<ge.yet.blockblast.feature.game.tray.TraySlotComponent>(),
                )
                override val selection =
                    com.arkivanov.decompose.value.MutableValue(ge.yet.blockblast.feature.game.tray.TraySelection.NONE)
                override fun clearSelection() {}
            }
        override fun onCellClicked(pieceId: Long, x: Int, y: Int) {
            engine.placePiece(pieceId, x, y)
            model.value = GameComponent.Model(game = engine.state.value)
        }
        override fun onReviveClicked() {
            reviveCalls += 1
            if (failRevive || !engine.continueWithSmallBlocks()) {
                onReviveFailed()
                return
            }
            model.value = GameComponent.Model(game = engine.state.value)
            externalScope.launch {
                val playableState = engine.state.value
                engine.saveNow(playableState)
                onReviveCompleted(playableState)
            }
        }
        override fun onRestartClicked() {}
        override fun onSettingsClicked() {}
        override fun onExitClicked() {}
        override fun onDismissSheet() {}
        fun complete(
            finalState: GameState,
            canContinue: Boolean,
            shouldRequestReview: Boolean = false,
        ) {
            onGameCompleted(finalState, canContinue, shouldRequestReview)
        }
        fun failRevive() = onReviveFailed()
    }

    private class OneByOneGenerator : ShapeGenerator {
        private val one = Polyomino("1x1", listOf(Position(0, 0)))
        override fun nextTray(seed: Long?): List<Polyomino> = listOf(one, one, one)
        override fun smallReviveTray(): List<Polyomino> = listOf(one, one, one)
    }

    private class RecordingGameSaveRepository(
        initial: GameState? = null,
    ) : GameSaveRepository {
        var saved: GameState? = initial
            private set
        var saveCount: Int = 0
            private set
        override suspend fun save(state: GameState) {
            saved = state
            saveCount += 1
        }
        override suspend fun load(): GameState? = saved
        override suspend fun clear() {
            saved = null
        }
    }

    private class RecordingResultFactory : GameResultComponent.Factory {
        val created = mutableListOf<FakeResult>()

        override fun create(
            componentContext: ComponentContext,
            snapshot: BlockBlastResultSnapshot,
            canContinue: Boolean,
            shouldRequestReview: Boolean,
            onContinueRequested: () -> Unit,
            onNewGameRequested: () -> Unit,
            onHomeRequested: () -> Unit,
        ): GameResultComponent =
            FakeResult(
                snapshot = snapshot,
                canContinue = canContinue,
                shouldRequestReview = shouldRequestReview,
                continueRequested = onContinueRequested,
                newGameRequested = onNewGameRequested,
                homeRequested = onHomeRequested,
            ).also { created += it }
    }

    private class FakeResult(
        val snapshot: BlockBlastResultSnapshot,
        val canContinue: Boolean,
        val shouldRequestReview: Boolean,
        val continueRequested: () -> Unit,
        val newGameRequested: () -> Unit,
        val homeRequested: () -> Unit,
    ) : GameResultComponent {
        var continueFailureCount = 0
        override val reviewPrompt = com.arkivanov.decompose.value.MutableValue(
            GameResultComponent.ReviewPromptSlot(
                component = if (shouldRequestReview) FakeReviewPrompt() else null,
            ),
        )
        override val model = com.arkivanov.decompose.value.MutableValue(
            GameResultComponent.Model(
                snapshot = snapshot,
                canContinue = canContinue,
                continueSecondsRemaining = 5,
            ),
        )

        override fun onPrimaryClicked(requestContinue: (onApproved: () -> Unit) -> Unit) {
            continueRequested()
        }

        override fun onHomeClicked() {
            homeRequested()
        }

        override fun onContinueFailed() {
            continueFailureCount += 1
        }

        override fun onDismissReviewPrompt(): Boolean {
            if (reviewPrompt.value.component == null) return false
            reviewPrompt.value = GameResultComponent.ReviewPromptSlot(component = null)
            return true
        }
    }

    private class FakeReviewPrompt : ReviewPromptComponent {
        override fun onDontShowAgainClicked() = Unit
        override fun onLeaveFeedbackClicked() = Unit
    }

    private class RecordingAudio : AudioRepository {
        var foregroundCount = 0
        var backgroundCount = 0
        override suspend fun playVoiceFeedback(type: FeedbackType) {}
        override suspend fun startMusic() {}
        override suspend fun stopMusic() {}
        override suspend fun onAppBackground() { backgroundCount += 1 }
        override suspend fun onAppForeground() { foregroundCount += 1 }
    }

    private class FakeSettings : SettingsRepository {
        val musicFlow = MutableStateFlow(true)
        val sfxFlow = MutableStateFlow(true)
        val vibrationFlow = MutableStateFlow(true)
        val darkFlow = MutableStateFlow(false)
        val adsFlow = MutableStateFlow(true)
        val tutorialFlow = MutableStateFlow(false)
        override val musicEnabled = musicFlow.asStateFlow()
        override val sfxEnabled = sfxFlow.asStateFlow()
        override val vibrationEnabled = vibrationFlow.asStateFlow()
        override val darkTheme = darkFlow.asStateFlow()
        override val adsEnabled = adsFlow.asStateFlow()
        override val tutorialSeen = tutorialFlow.asStateFlow()
        override val bestScore = MutableStateFlow(0L).asStateFlow()
        override val reviewPromptCount = MutableStateFlow(0).asStateFlow()
        override suspend fun setMusicEnabled(enabled: Boolean) { musicFlow.value = enabled }
        override suspend fun setSfxEnabled(enabled: Boolean) { sfxFlow.value = enabled }
        override suspend fun setVibrationEnabled(enabled: Boolean) { vibrationFlow.value = enabled }
        override suspend fun setDarkTheme(enabled: Boolean) { darkFlow.value = enabled }
        override suspend fun setAdsEnabled(enabled: Boolean) { adsFlow.value = enabled }
        override suspend fun setBestScore(score: Long) {}
        override suspend fun incrementReviewPromptCount() {}
        override suspend fun suppressReviewPrompts(max: Int) {}
        override suspend fun setTutorialSeen() { tutorialFlow.value = true }
    }
}
