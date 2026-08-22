package ge.yet.game.blockblast.di

import androidx.compose.runtime.Composable
import com.app.common.AppDispatchers
import com.app.common.di.CommonBindings
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import ge.yet.game.blockblast.BlockBlastPlugin
import ge.yet.game.blockblast.component.game.GameComponent
import ge.yet.game.blockblast.component.game.store.ReviewOpportunityConfig
import ge.yet.game.blockblast.domain.engine.GameSessionReducer
import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.blockblast.domain.model.Grid
import ge.yet.game.blockblast.domain.model.Piece
import ge.yet.game.blockblast.domain.model.Polyomino
import ge.yet.game.blockblast.domain.model.Position
import ge.yet.game.blockblast.domain.repository.BestScoreRepository
import ge.yet.game.blockblast.domain.repository.GameSaveRepository
import ge.yet.game.blockblast.session.BlockBlastSession
import ge.yet.game.blockblast.session.BlockBlastSessionComponent
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.domain.repository.AudioRepository
import ge.yet.game.domain.repository.FeedbackPreferences
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.compose.MiniAppInterstitialCapability
import ge.yet.game.miniapp.compose.MiniAppInterstitialGate
import ge.yet.game.miniapp.compose.MiniAppInterstitialPlacement
import ge.yet.game.miniapp.compose.MiniAppRegistry
import ge.yet.game.miniapp.metro.MiniAppMetroBindings
import ge.yet.game.miniapp.metro.MiniAppSessionScope
import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import ge.yet.game.miniapp.testkit.RecordingMiniAppSessionHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [
        MiniAppMetroBindings::class,
        BlockBlastAppBindings::class,
        BlockBlastGraphTestBindings::class,
    ],
)
internal interface BlockBlastPluginTestGraph {
    val registry: MiniAppRegistry
    val sessionFactory: BlockBlastSessionGraph.Factory
    val inspectableSessionFactory: InspectableBlockBlastSessionGraph.Factory
    val saveRepository: GameSaveRepository
    val bestScoreRepository: BestScoreRepository
    val feedbackPreferences: FeedbackPreferences
    val settings: Settings
    val audioRepository: AudioRepository
    val appScope: CoroutineScope
}

@GraphExtension(MiniAppSessionScope::class)
internal interface InspectableBlockBlastSessionGraph {
    val session: BlockBlastSession
    val sessionComponent: BlockBlastSessionComponent
    val gameReducer: GameSessionReducer
    val saveRepository: GameSaveRepository
    val bestScoreRepository: BestScoreRepository
    val feedbackPreferences: FeedbackPreferences
    val componentContext: ComponentContext
    val visibility: MiniAppVisibilitySource
    val host: MiniAppSessionHost

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createInspectable(
            @Provides componentContext: ComponentContext,
            @Provides visibility: MiniAppVisibilitySource,
            @Provides host: MiniAppSessionHost,
        ): InspectableBlockBlastSessionGraph
    }
}

@OptIn(ExperimentalSettingsApi::class)
@ContributesTo(
    scope = AppScope::class,
    replaces = [CommonBindings::class],
)
@BindingContainer
internal object BlockBlastGraphTestBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideMapSettings(): MapSettings = MapSettings()

    @Provides
    fun provideSettings(settings: MapSettings): Settings = settings

    @Provides
    fun provideObservableSettings(settings: MapSettings): ObservableSettings = settings

    @Provides
    @SingleIn(AppScope::class)
    fun provideStoreFactory(): StoreFactory = DefaultStoreFactory()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDispatchers(): AppDispatchers = AppDispatchers(
        default = Dispatchers.Main,
        io = Dispatchers.Main,
        main = Dispatchers.Main,
    )

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppScope(dispatchers: AppDispatchers): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatchers.default)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAudioRepository(): AudioRepository = RecordingAudioRepository()

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnalytics(): AnalyticRepository = NoOpAnalyticsRepository

    @Provides
    @SingleIn(AppScope::class)
    fun provideFeedbackPreferences(): FeedbackPreferences = TestFeedbackPreferences()

    @Provides
    @SingleIn(AppScope::class)
    fun provideInterstitialCapability(): MiniAppInterstitialCapability = NoOpInterstitialCapability
}

@OptIn(ExperimentalCoroutinesApi::class)
class BlockBlastSessionGraphTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun two_child_graphs_have_distinct_session_components_and_reducers() = runTest(dispatcher) {
        val appGraph = createGraph<BlockBlastPluginTestGraph>()
        val firstLifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val secondLifecycle = MiniAppLifecycleHarness().also { it.resume() }
        try {
            val first = appGraph.inspectableSessionFactory.createInspectable(
                firstLifecycle.componentContext,
                MutableMiniAppVisibilitySource(),
                RecordingMiniAppSessionHost(),
            )
            val second = appGraph.inspectableSessionFactory.createInspectable(
                secondLifecycle.componentContext,
                MutableMiniAppVisibilitySource(),
                RecordingMiniAppSessionHost(),
            )
            runCurrent()

            assertNotSame(first.session, second.session)
            assertNotSame(first.sessionComponent, second.sessionComponent)
            assertNotSame(first.gameReducer, second.gameReducer)
            assertNotSame(first.playing(), second.playing())
        } finally {
            appGraph.destroySessionsAndCancelAppScope(firstLifecycle, secondLifecycle)
            runCurrent()
        }
    }

    @Test
    fun two_child_graphs_share_app_scoped_save_and_best_score_repositories() {
        val appGraph = createGraph<BlockBlastPluginTestGraph>()
        val firstLifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val secondLifecycle = MiniAppLifecycleHarness().also { it.resume() }
        try {
            val first = appGraph.inspectableSessionFactory.createInspectable(
                firstLifecycle.componentContext,
                MutableMiniAppVisibilitySource(),
                RecordingMiniAppSessionHost(),
            )
            val second = appGraph.inspectableSessionFactory.createInspectable(
                secondLifecycle.componentContext,
                MutableMiniAppVisibilitySource(),
                RecordingMiniAppSessionHost(),
            )

            assertSame(appGraph.saveRepository, first.saveRepository)
            assertSame(first.saveRepository, second.saveRepository)
            assertSame(appGraph.bestScoreRepository, first.bestScoreRepository)
            assertSame(first.bestScoreRepository, second.bestScoreRepository)
        } finally {
            appGraph.destroySessionsAndCancelAppScope(firstLifecycle, secondLifecycle)
        }
    }

    @Test
    fun child_graph_inherits_the_parent_feedback_preferences_instance() {
        val appGraph = createGraph<BlockBlastPluginTestGraph>()
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
        try {
            val graph = appGraph.inspectableSessionFactory.createInspectable(
                lifecycle.componentContext,
                MutableMiniAppVisibilitySource(),
                RecordingMiniAppSessionHost(),
            )

            assertSame(appGraph.feedbackPreferences, graph.feedbackPreferences)
            assertSame(
                appGraph.feedbackPreferences,
                assertIs<BlockBlastSession>(graph.session).feedback,
            )
        } finally {
            appGraph.destroySessionsAndCancelAppScope(lifecycle)
        }
    }

    @Test
    fun runtime_component_context_visibility_and_host_reach_only_the_created_session() = runTest(dispatcher) {
        val appGraph = createGraph<BlockBlastPluginTestGraph>()
        val firstLifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val secondLifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val firstVisibility = MutableMiniAppVisibilitySource(MiniAppVisibility.OBSCURED)
        val secondVisibility = MutableMiniAppVisibilitySource(MiniAppVisibility.ACTIVE)
        val firstHost = RecordingMiniAppSessionHost()
        val secondHost = RecordingMiniAppSessionHost()
        try {
            appGraph.saveRepository.save(qualifyingStateOneMoveFromGameOver())
            val first = appGraph.inspectableSessionFactory.createInspectable(
                firstLifecycle.componentContext,
                firstVisibility,
                firstHost,
            )
            val second = appGraph.inspectableSessionFactory.createInspectable(
                secondLifecycle.componentContext,
                secondVisibility,
                secondHost,
            )
            val firstGame = first.playing()
            val secondGame = second.playing()
            runCurrent()

            assertSame(firstLifecycle.componentContext, first.componentContext)
            assertSame(firstVisibility, first.visibility)
            assertSame(firstHost, first.host)
            assertSame(secondLifecycle.componentContext, second.componentContext)
            assertSame(secondVisibility, second.visibility)
            assertSame(secondHost, second.host)
            assertNotSame(first.componentContext, second.componentContext)
            assertNotSame(first.sessionComponent, second.sessionComponent)
            assertSame(first.sessionComponent, assertIs<BlockBlastSession>(first.session).component)
            assertSame(second.sessionComponent, assertIs<BlockBlastSession>(second.session).component)

            val firstBefore = firstGame.model.value.game
            val secondBefore = secondGame.model.value.game
            firstGame.onCellClicked(pieceId = 1L, x = 1, y = 0)
            secondGame.onCellClicked(pieceId = 1L, x = 1, y = 0)
            runCurrent()

            assertEquals(firstBefore, firstGame.model.value.game)
            assertTrue(secondGame.model.value.game.score > secondBefore.score)
            assertIs<BlockBlastSessionComponent.Child.Playing>(
                first.sessionComponent.stack.value.active.instance,
            )
            assertIs<BlockBlastSessionComponent.Child.Result>(
                second.sessionComponent.stack.value.active.instance,
            )
            assertTrue(firstHost.reviewRequests.isEmpty())
            val review = secondHost.reviewRequests.single()
            assertEquals("block_blast_result", review.triggerId)
            assertEquals(secondGame.model.value.game.score, review.score)
        } finally {
            appGraph.destroySessionsAndCancelAppScope(firstLifecycle, secondLifecycle)
            runCurrent()
        }
    }

    @Test
    fun destroying_the_child_lifecycle_cancels_session_jobs_once() = runTest(dispatcher) {
        val appGraph = createGraph<BlockBlastPluginTestGraph>()
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
        var appScopeJob: Job? = null
        try {
            appScopeJob = assertNotNull(appGraph.appScope.coroutineContext[Job])
            val graph = appGraph.inspectableSessionFactory.createInspectable(
                lifecycle.componentContext,
                MutableMiniAppVisibilitySource(),
                RecordingMiniAppSessionHost(),
            )
            graph.playing()
            runCurrent()
            val audio = assertIs<RecordingAudioRepository>(appGraph.audioRepository)
            assertEquals(1, audio.musicJobsStarted)

            lifecycle.destroy()
            lifecycle.destroy()
            runCurrent()

            assertEquals(1, audio.musicJobsCancelled)
            assertFalse(appScopeJob.isCancelled)
        } finally {
            appGraph.destroySessionsAndCancelAppScope(lifecycle)
            runCurrent()
        }
        assertTrue(assertNotNull(appScopeJob).isCancelled)
    }

    @Test
    fun existing_save_bytes_are_read_without_key_or_serializer_migration() = runTest(dispatcher) {
        val appGraph = createGraph<BlockBlastPluginTestGraph>()
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
        var createdGraph: BlockBlastSessionGraph? = null
        try {
            appGraph.settings.putString("blockblast.game_save", CURRENT_SAVE_BYTES)
            val plugin = BlockBlastPlugin { componentContext, visibility, host ->
                appGraph.sessionFactory.createGameBlockblastSessionGraph(
                    componentContext,
                    visibility,
                    host
                ).also {
                    createdGraph = it
                }
            }

            plugin.createSession(
                componentContext = lifecycle.componentContext,
                visibility = MutableMiniAppVisibilitySource(),
                host = RecordingMiniAppSessionHost(),
            )
            runCurrent()

            val session = assertIs<BlockBlastSession>(assertNotNull(createdGraph).session)
            val restored = session.component.playing().model.value.game
            assertEquals(4_321L, restored.score)
            assertEquals(5, restored.grid.colorAt(0, 0))
            assertEquals(-1, restored.grid.colorAt(1, 0))
            assertEquals(1, restored.currentPieces.size)
            assertEquals(77L, restored.currentPieces.single().pieceId)
            assertEquals("h2", restored.currentPieces.single().shape.id)
            assertEquals(2, restored.currentPieces.single().shape.cells.size)
        } finally {
            appGraph.destroySessionsAndCancelAppScope(lifecycle)
            runCurrent()
        }
    }
}

private fun InspectableBlockBlastSessionGraph.playing(): GameComponent = sessionComponent.playing()

private fun BlockBlastSessionComponent.playing(): GameComponent =
    assertIs<BlockBlastSessionComponent.Child.Playing>(stack.value.active.instance).component

internal fun BlockBlastPluginTestGraph.destroySessionsAndCancelAppScope(
    vararg lifecycles: MiniAppLifecycleHarness,
) {
    try {
        lifecycles.forEach { lifecycle ->
            lifecycle.destroy()
            lifecycle.destroy()
        }
    } finally {
        appScope.cancel()
    }
}

private fun qualifyingStateOneMoveFromGameOver(): GameState {
    var grid = Grid()
    for (y in 0 until Grid.SIZE) {
        for (x in 0 until Grid.SIZE) {
            if ((x + y) % 2 == 0) grid = grid.withCell(x, y, colorId = 3)
        }
    }
    val qualifyingScore =
        ReviewOpportunityConfig.MIN_SCORE + ReviewOpportunityConfig.BEST_SCORE_DELTA + 10L
    return GameState(
        grid = grid,
        score = qualifyingScore,
        bestScore = qualifyingScore,
        bestAtRoundStart = 0L,
        currentPieces = listOf(
            Piece(
                pieceId = 1L,
                shape = Polyomino("single", listOf(Position(0, 0))),
                colorId = 1,
            ),
            Piece(
                pieceId = 2L,
                shape = Polyomino(
                    "horizontal_two",
                    listOf(Position(0, 0), Position(1, 0)),
                ),
                colorId = 2,
            ),
        ),
        nextPieceId = 2L,
    )
}

private class RecordingAudioRepository : AudioRepository {
    var musicJobsStarted = 0
        private set
    var musicJobsCancelled = 0
        private set

    override suspend fun playSound(filename: String) = Unit
    override suspend fun startMusic(tracks: List<String>) {
        musicJobsStarted += 1
        try {
            awaitCancellation()
        } finally {
            musicJobsCancelled += 1
        }
    }

    override suspend fun stopMusic() = Unit
    override suspend fun onAppBackground() = Unit
    override suspend fun onAppForeground() = Unit
}

private data object NoOpAnalyticsRepository : AnalyticRepository {
    override fun logEvent(eventName: String, params: Map<String, Any>?) = Unit
    override fun deleteData() = Unit
}

private class TestFeedbackPreferences : FeedbackPreferences {
    private val sfx = MutableStateFlow(true)
    private val vibration = MutableStateFlow(true)
    override val sfxEnabled: StateFlow<Boolean> = sfx.asStateFlow()
    override val vibrationEnabled: StateFlow<Boolean> = vibration.asStateFlow()
}

private data object NoOpInterstitialCapability : MiniAppInterstitialCapability {
    @Composable
    override fun rememberGate(placement: MiniAppInterstitialPlacement): MiniAppInterstitialGate =
        MiniAppInterstitialGate(
            willShowAd = false,
            request = { onComplete -> onComplete() },
        )
}

private const val CURRENT_SAVE_BYTES =
    """{"version":1,"state":{"grid":{"cells":[5,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1]},"score":4321,"bestScore":9000,"currentPieces":[{"pieceId":77,"shape":{"id":"h2","cells":[{"x":0,"y":0},{"x":1,"y":0}]},"colorId":3}],"nextPieceId":77}}"""
