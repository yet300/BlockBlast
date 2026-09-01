package ge.yet.game.fruitmerge.session

import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.game.fruitmerge.TestFruitMergeRules
import ge.yet.game.fruitmerge.audio.FruitMergeAudioAdapter
import ge.yet.game.fruitmerge.engine.FruitMergeState
import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.engine.RunPhase
import ge.yet.game.fruitmerge.engine.Vec2
import ge.yet.game.fruitmerge.persistence.FruitMergePersistence
import ge.yet.game.fruitmerge.store.FruitMergeStoreFactory
import ge.yet.game.fruitmerge.store.FruitMergeStore
import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import ge.yet.game.miniapp.testkit.MutableMiniAppStorage
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import ge.yet.game.miniapp.testkit.NoopMiniAppAudio
import ge.yet.game.miniapp.compose.MiniAppFrameMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FruitMergeSessionComponentTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `terminal restore and restart reuse the same game component`() = runTest {
        val storage = MutableMiniAppStorage()
        val persistence = FruitMergePersistence(storage)
        persistence.checkpoint(FruitMergeState(runOrdinal = 7L, phase = RunPhase.RESULT))
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val rules = TestFruitMergeRules()
        val component = DefaultFruitMergeSessionComponent(
            componentContext = lifecycle.componentContext,
            storeFactory = FruitMergeStoreFactory(
                storeFactory = DefaultStoreFactory(),
                rules = rules,
                persistence = persistence,
            ),
            persistence = persistence,
            visibility = MutableMiniAppVisibilitySource(),
            audio = FruitMergeAudioAdapter(NoopMiniAppAudio),
        )

        advanceUntilIdle()
        val game = component.game
        assertIs<FruitMergeComponent.ScreenState.GameOver>(game.model.value.screen)
        assertEquals(MiniAppFrameMode.ContentOnly, component.frameMode.value)
        assertEquals(false, game.handleBack())

        game.newGame()
        advanceUntilIdle()

        assertSame(game, component.game)
        assertIs<FruitMergeComponent.ScreenState.Playing>(game.model.value.screen)
        assertEquals(MiniAppFrameMode.Standard, component.frameMode.value)
        lifecycle.destroy()
    }

    @Test
    fun `tutorial follows an accepted drop merge and finite trait reveal`() = runTest {
        val storage = MutableMiniAppStorage()
        val persistence = FruitMergePersistence(storage)
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val component = DefaultFruitMergeSessionComponent(
            componentContext = lifecycle.componentContext,
            storeFactory = FruitMergeStoreFactory(
                storeFactory = DefaultStoreFactory(),
                rules = TestFruitMergeRules(),
                persistence = persistence,
            ),
            persistence = persistence,
            visibility = MutableMiniAppVisibilitySource(),
            audio = FruitMergeAudioAdapter(NoopMiniAppAudio),
        )
        advanceUntilIdle()
        val playing = component.game

        assertIs<TutorialStep.Gesture>(playing.model.value.tutorialStep)
        playing.drop(dragged = false)
        advanceUntilIdle()
        assertIs<TutorialStep.Merge>(playing.model.value.tutorialStep)

        component.gameComponent.onStoreLabel(
            FruitMergeStore.Label.MergeResolved(FruitLevel.RASPBERRY, Vec2(0.5f, 0.6f)),
        )
        assertIs<TutorialStep.Traits>(playing.model.value.tutorialStep)
        playing.completeTutorial()
        advanceUntilIdle()

        kotlin.test.assertEquals(null, playing.model.value.tutorialStep)
        kotlin.test.assertTrue(FruitMergePersistence(storage).isTutorialSeen())
        lifecycle.destroy()
    }

    @Test
    fun `active shake blocks another free action at the component boundary`() = runTest {
        val storage = MutableMiniAppStorage()
        val persistence = FruitMergePersistence(storage)
        persistence.checkpoint(
            FruitMergeState(
                bodies = listOf(
                    ge.yet.game.fruitmerge.engine.FruitBody(
                        id = 1L,
                        level = ge.yet.game.fruitmerge.engine.FruitLevel.APPLE,
                        position = ge.yet.game.fruitmerge.engine.Vec2(0.5f, 0.8f),
                    ),
                ),
                nextBodyId = 2L,
            ),
        )
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val rules = TestFruitMergeRules()
        val component = DefaultFruitMergeSessionComponent(
            componentContext = lifecycle.componentContext,
            storeFactory = FruitMergeStoreFactory(
                storeFactory = DefaultStoreFactory(),
                rules = rules,
                persistence = persistence,
            ),
            persistence = persistence,
            visibility = MutableMiniAppVisibilitySource(),
            audio = FruitMergeAudioAdapter(NoopMiniAppAudio),
        )
        advanceUntilIdle()
        val playing = component.game

        assertNull(playing.requestShakeGate())
        advanceUntilIdle()
        val active = playing.model.value.game
        assertNull(playing.requestShakeGate())

        assertEquals(FruitMergeState.FREE_SHAKE_COUNT - 1, active.freeShakes)
        assertEquals(active, playing.model.value.game)
        assertEquals(1, rules.shakeCalls)
        lifecycle.destroy()
    }

    @Test
    fun `visible committed labels bridge to bounded presentation events`() = runTest {
        val storage = MutableMiniAppStorage()
        val persistence = FruitMergePersistence(storage)
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val component = DefaultFruitMergeSessionComponent(
            componentContext = lifecycle.componentContext,
            storeFactory = FruitMergeStoreFactory(DefaultStoreFactory(), TestFruitMergeRules(), persistence),
            persistence = persistence,
            visibility = MutableMiniAppVisibilitySource(),
            audio = FruitMergeAudioAdapter(NoopMiniAppAudio),
        )
        advanceUntilIdle()
        assertTrue(component.game.model.value.visible)
        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            component.game.presentationEvents.take(4).toList()
        }

        component.gameComponent.onStoreLabel(FruitMergeStore.Label.FruitLanded(FruitLevel.LIME, Vec2(0.2f, 0.8f)))
        component.gameComponent.onStoreLabel(FruitMergeStore.Label.MergeResolved(FruitLevel.MANDARIN, Vec2(0.4f, 0.7f)))
        component.gameComponent.onStoreLabel(FruitMergeStore.Label.ClearApplied(FruitLevel.APPLE, Vec2(0.6f, 0.7f)))
        component.gameComponent.onStoreLabel(FruitMergeStore.Label.ShakePulse(3))
        val received = collector.await()

        assertEquals(
            listOf(
                FruitMergeComponent.PresentationEvent.Landing(FruitLevel.LIME, Vec2(0.2f, 0.8f)),
                FruitMergeComponent.PresentationEvent.Merge(FruitLevel.MANDARIN, Vec2(0.4f, 0.7f)),
                FruitMergeComponent.PresentationEvent.Clear(FruitLevel.APPLE, Vec2(0.6f, 0.7f)),
                FruitMergeComponent.PresentationEvent.ShakePulse(3),
            ),
            received,
        )
        lifecycle.destroy()
    }
}
