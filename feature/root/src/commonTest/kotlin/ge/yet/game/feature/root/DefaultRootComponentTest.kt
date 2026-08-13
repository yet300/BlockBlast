package ge.yet.game.feature.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.arkivanov.essenty.statekeeper.StateKeeper
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import ge.yet.game.blockblast.component.game.GameComponent
import ge.yet.game.blockblast.component.result.BlockBlastResultSnapshot
import ge.yet.game.blockblast.component.result.GameResultComponent
import ge.yet.game.blockblast.component.tray.PieceTrayComponent
import ge.yet.game.blockblast.component.tray.TraySelection
import ge.yet.game.blockblast.component.tray.TraySlotComponent
import ge.yet.game.feature.home.HomeComponent
import ge.yet.game.feature.review.AppReviewComponent
import ge.yet.game.feature.review.policy.AppReviewPolicy
import ge.yet.game.feature.settings.SettingsComponent
import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.blockblast.domain.model.Grid
import ge.yet.game.blockblast.domain.model.Piece
import ge.yet.game.blockblast.domain.model.Polyomino
import ge.yet.game.blockblast.domain.model.Position
import ge.yet.game.domain.repository.AudioRepository
import ge.yet.game.blockblast.domain.repository.GameSaveRepository
import ge.yet.game.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRootComponentTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun build(
        stateKeeper: StateKeeper? = null,
        gameFactory: RecordingGameFactory = RecordingGameFactory(),
        resultFactory: RecordingResultFactory = RecordingResultFactory(),
        settingsFactory: RecordingSettingsFactory = RecordingSettingsFactory(),
        reviewFactory: RecordingReviewFactory = RecordingReviewFactory(),
        reviewPolicy: RecordingReviewPolicy = RecordingReviewPolicy(),
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
            settingsFactory = settingsFactory,
            gameFactory = gameFactory,
            resultFactory = resultFactory,
            reviewFactory = reviewFactory,
            reviewPolicy = reviewPolicy,
            audio = audio,
            settingsRepository = settings,
        )
        return Setup(
            component,
            lifecycle,
            audio,
            settings,
            homeFactory,
            gameFactory,
            resultFactory,
            settingsFactory,
            reviewFactory,
            reviewPolicy,
        )
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
        settings.adsFlow.value = false
        assertFalse(component.adsEnabled.value)
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
    fun game_settings_click_opens_root_settings_sheet() {
        val setup = build()
        setup.homeFactory.created.first().onNewGameClicked(true)

        setup.gameFactory.created.single().onSettingsClicked()

        assertIs<RootComponent.SheetChild.Settings>(
            setup.component.sheetSlot.value.child?.instance,
        )
        assertIs<RootComponent.Child.Game>(setup.component.stack.value.active.instance)
    }

    @Test
    fun root_dismiss_closes_settings_sheet() {
        val setup = build()
        setup.homeFactory.created.first().onNewGameClicked(true)
        setup.gameFactory.created.single().onSettingsClicked()

        setup.component.onDismissSheet()

        assertNull(setup.component.sheetSlot.value.child)
    }

    @Test
    fun settings_back_callback_closes_root_sheet() {
        val setup = build()
        setup.homeFactory.created.first().onNewGameClicked(true)
        setup.gameFactory.created.single().onSettingsClicked()

        setup.settingsFactory.created.single().onBackClicked()

        assertNull(setup.component.sheetSlot.value.child)
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
    fun game_completion_pushes_result_and_opens_app_review_sheet_when_policy_allows() {
        val setup = build()
        setup.homeFactory.created.first().onNewGameClicked(true)
        val finalState = resultState()
        setup.gameFactory.created.single().complete(
            finalState,
            canContinue = true,
            reviewOpportunity = true,
        )
        assertIs<RootComponent.Child.Result>(setup.component.stack.value.active.instance)
        assertEquals(BlockBlastResultSnapshot.from(finalState), setup.resultFactory.created.single().snapshot)
        assertEquals(finalState, setup.gameFactory.created.last().restoredResultState)
        assertTrue(setup.resultFactory.created.single().canContinue)
        assertEquals(1, setup.reviewPolicy.acquireCalls)
        val review = assertIs<RootComponent.SheetChild.AppReview>(
            setup.component.sheetSlot.value.child?.instance,
        ).component
        assertEquals(review, setup.reviewFactory.created.single())
        assertEquals(
            mapOf(
                "source" to "block_blast_result",
                "score" to finalState.score,
                "best_score" to finalState.bestScore,
                "revives_used" to finalState.revivesUsed,
            ),
            setup.reviewFactory.analyticsParams.single(),
        )
    }

    @Test
    fun back_dismisses_app_review_sheet_before_leaving_result() {
        val setup = build()
        setup.homeFactory.created.first().onNewGameClicked(true)
        setup.gameFactory.created.single().complete(
            resultState(),
            canContinue = true,
            reviewOpportunity = true,
        )
        val result = setup.resultFactory.created.single()
        result.model.value = result.model.value.copy(continueSecondsRemaining = 3)

        setup.component.onBackClicked()

        assertIs<RootComponent.Child.Result>(setup.component.stack.value.active.instance)
        assertEquals(1, setup.resultFactory.created.size)
        assertEquals(3, result.model.value.continueSecondsRemaining)
        assertNull(setup.component.sheetSlot.value.child)
        setup.component.onBackClicked()
        assertIs<RootComponent.Child.Home>(setup.component.stack.value.active.instance)
    }

    @Test
    fun review_action_requests_root_to_close_sheet() {
        val setup = build()
        setup.homeFactory.created.first().onNewGameClicked(true)
        setup.gameFactory.created.single().complete(
            resultState(),
            canContinue = true,
            reviewOpportunity = true,
        )

        setup.reviewFactory.created.single().onLeaveFeedbackClicked()

        assertNull(setup.component.sheetSlot.value.child)
    }

    @Test
    fun game_completion_does_not_open_review_sheet_when_global_policy_rejects() {
        val policy = RecordingReviewPolicy(allow = false)
        val setup = build(reviewPolicy = policy)
        setup.homeFactory.created.first().onNewGameClicked(true)

        setup.gameFactory.created.single().complete(
            resultState(),
            canContinue = true,
            reviewOpportunity = true,
        )

        assertIs<RootComponent.Child.Result>(setup.component.stack.value.active.instance)
        assertEquals(1, policy.acquireCalls)
        assertNull(setup.component.sheetSlot.value.child)
        assertTrue(setup.reviewFactory.created.isEmpty())
    }

    @Test
    fun game_completion_without_game_opportunity_does_not_consult_global_policy() {
        val setup = build()
        setup.homeFactory.created.first().onNewGameClicked(true)

        setup.gameFactory.created.single().complete(
            resultState(),
            canContinue = true,
            reviewOpportunity = false,
        )

        assertEquals(0, setup.reviewPolicy.acquireCalls)
        assertNull(setup.component.sheetSlot.value.child)
    }

    @Test
    fun duplicate_game_completion_does_not_push_duplicate_result() {
        val setup = build()
        setup.homeFactory.created.first().onNewGameClicked(true)
        val game = setup.gameFactory.created.single()
        val finalState = resultState()
        game.complete(finalState, canContinue = true, reviewOpportunity = true)
        game.complete(finalState, canContinue = true, reviewOpportunity = true)
        assertEquals(3, setup.component.stack.value.items.size)
        assertIs<RootComponent.Child.Result>(setup.component.stack.value.active.instance)
        assertEquals(1, setup.reviewPolicy.acquireCalls)
        assertEquals(1, setup.reviewFactory.created.size)
    }

    @Test
    fun open_review_sheet_restores_without_reacquiring_global_policy() {
        val stateKeeper = StateKeeperDispatcher()
        val first = build(stateKeeper = stateKeeper)
        first.homeFactory.created.first().onNewGameClicked(true)
        first.gameFactory.created.single().complete(
            resultState(),
            canContinue = true,
            reviewOpportunity = true,
        )
        val saved = stateKeeper.save()
        first.lifecycle.destroy()

        val restoredPolicy = RecordingReviewPolicy()
        val restored = build(
            stateKeeper = StateKeeperDispatcher(saved),
            reviewPolicy = restoredPolicy,
        )

        assertIs<RootComponent.Child.Result>(restored.component.stack.value.active.instance)
        assertIs<RootComponent.SheetChild.AppReview>(
            restored.component.sheetSlot.value.child?.instance,
        )
        assertEquals(0, restoredPolicy.acquireCalls)
        assertEquals(1, restored.reviewFactory.created.size)
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
        assertEquals(finalState.score, restoredGame.model.value.game.score)
        assertEquals(finalState.grid, restoredGame.model.value.game.grid)
        assertTrue(restoredGame.model.value.game.isGameOver)

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
        assertEquals(playableState.score, freshGame.model.value.game.score)
        assertEquals(playableState.grid, freshGame.model.value.game.grid)
        assertEquals(playableState.revivesUsed, freshGame.model.value.game.revivesUsed)
        assertFalse(freshGame.model.value.game.isGameOver)

        val saveCountBeforePlacement = freshRepository.saveCount
        val piece = freshGame.model.value.game.currentPieces.first()
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
        val settingsFactory: RecordingSettingsFactory,
        val reviewFactory: RecordingReviewFactory,
        val reviewPolicy: RecordingReviewPolicy,
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

    private class RecordingSettingsFactory : SettingsComponent.Factory {
        val created = mutableListOf<FakeSettingsComponent>()

        override fun create(
            componentContext: ComponentContext,
            onBackClicked: () -> Unit,
        ): SettingsComponent =
            FakeSettingsComponent(componentContext, onBackClicked).also { created += it }
    }

    private class FakeSettingsComponent(
        componentContext: ComponentContext,
        private val onBackClicked: () -> Unit,
    ) : SettingsComponent, ComponentContext by componentContext {
        override val stack
            get() = error("FakeSettingsComponent.stack must not be read in tests")

        override fun onBackClicked() = onBackClicked.invoke()
    }

    private class FakeHome(
        val onContinueClicked: (Boolean) -> Unit,
        val onNewGameClicked: (Boolean) -> Unit,
    ) : HomeComponent {
        override val model = MutableValue(
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
            onSettingsClicked: () -> Unit,
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
                onSettingsClicked = onSettingsClicked,
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
        private val onSettingsClicked: () -> Unit,
        private val onGameCompleted: (GameState, Boolean, Boolean) -> Unit,
        private val onReviveCompleted: (GameState) -> Unit,
        private val onReviveFailed: () -> Unit,
    ) : GameComponent {
        var reviveCalls = 0
        private val playablePiece = Piece(
            pieceId = 1L,
            shape = Polyomino("1x1", listOf(Position(0, 0))),
            colorId = 0,
        )
        private var gameState: GameState = when {
            restoredResultState != null -> restoredResultState
            !isNewGame && saveRepository.saved != null -> checkNotNull(saveRepository.saved)
            else -> GameState(currentPieces = listOf(playablePiece))
        }

        override val model = MutableValue(
            GameComponent.Model(game = gameState),
        )
        override val tutorialSeen = MutableStateFlow(true).asStateFlow()
        override val pieceTray: PieceTrayComponent =
            object : PieceTrayComponent {
                override val slots = MutableValue(
                    emptyList<TraySlotComponent>(),
                )
                override val selection =
                    MutableValue(TraySelection.NONE)
                override fun clearSelection() {}
        }
        override fun onCellClicked(pieceId: Long, x: Int, y: Int) {
            gameState = gameState.copy(currentPieces = gameState.currentPieces.filter { it.pieceId != pieceId })
            externalScope.launch { saveRepository.save(gameState) }
            model.value = GameComponent.Model(game = gameState)
        }
        override fun onReviveClicked() {
            reviveCalls += 1
            if (failRevive || gameState.revivesUsed >= GameState.MAX_REVIVES) {
                onReviveFailed()
                return
            }
            gameState = gameState.copy(
                currentPieces = gameState.currentPieces.ifEmpty { listOf(playablePiece) },
                isGameOver = false,
                revivesUsed = gameState.revivesUsed + 1,
            )
            model.value = GameComponent.Model(game = gameState)
            externalScope.launch {
                val playableState = gameState
                saveRepository.save(playableState)
                onReviveCompleted(playableState)
            }
        }
        override fun onRestartClicked() {}
        override fun onSettingsClicked() = onSettingsClicked.invoke()
        override fun onExitClicked() {}
        override fun onTutorialSeen() {}
        fun complete(
            finalState: GameState,
            canContinue: Boolean,
            reviewOpportunity: Boolean = false,
        ) {
            onGameCompleted(finalState, canContinue, reviewOpportunity)
        }
        fun failRevive() = onReviveFailed()
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
            onContinueRequested: () -> Unit,
            onNewGameRequested: () -> Unit,
            onHomeRequested: () -> Unit,
        ): GameResultComponent =
            FakeResult(
                snapshot = snapshot,
                canContinue = canContinue,
                continueRequested = onContinueRequested,
                newGameRequested = onNewGameRequested,
                homeRequested = onHomeRequested,
            ).also { created += it }
    }

    private class FakeResult(
        val snapshot: BlockBlastResultSnapshot,
        val canContinue: Boolean,
        val continueRequested: () -> Unit,
        val newGameRequested: () -> Unit,
        val homeRequested: () -> Unit,
    ) : GameResultComponent {
        var continueFailureCount = 0
        override val model = MutableValue(
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

    }

    private class RecordingReviewFactory : AppReviewComponent.Factory {
        val created = mutableListOf<FakeReview>()
        val analyticsParams = mutableListOf<Map<String, Any>>()

        override fun create(
            componentContext: ComponentContext,
            analyticsParams: Map<String, Any>,
            onCloseRequested: () -> Unit,
        ): AppReviewComponent {
            this.analyticsParams += analyticsParams
            return FakeReview(onCloseRequested).also(created::add)
        }
    }

    private class RecordingReviewPolicy(
        private val allow: Boolean = true,
    ) : AppReviewPolicy {
        var acquireCalls = 0

        override suspend fun tryAcquirePrompt(): Boolean {
            acquireCalls += 1
            return allow
        }
    }

    private class FakeReview(
        private val onCloseRequested: () -> Unit,
    ) : AppReviewComponent {
        override fun onDontShowAgainClicked() = onCloseRequested()
        override fun onLeaveFeedbackClicked() = onCloseRequested()
    }

    private class RecordingAudio : AudioRepository {
        var foregroundCount = 0
        var backgroundCount = 0
        override suspend fun playSound(filename: String) {}
        override suspend fun startMusic(tracks: List<String>) {}
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
        override val musicEnabled = musicFlow.asStateFlow()
        override val sfxEnabled = sfxFlow.asStateFlow()
        override val vibrationEnabled = vibrationFlow.asStateFlow()
        override val darkTheme = darkFlow.asStateFlow()
        override val adsEnabled = adsFlow.asStateFlow()
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
    }
}
