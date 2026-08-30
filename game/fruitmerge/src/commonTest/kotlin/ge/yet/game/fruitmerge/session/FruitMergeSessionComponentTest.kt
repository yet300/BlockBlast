package ge.yet.game.fruitmerge.session

import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.game.fruitmerge.TestFruitMergeRules
import ge.yet.game.fruitmerge.audio.FruitMergeAudioAdapter
import ge.yet.game.fruitmerge.engine.FruitMergeState
import ge.yet.game.fruitmerge.engine.RunPhase
import ge.yet.game.fruitmerge.persistence.FruitMergePersistence
import ge.yet.game.fruitmerge.store.FruitMergeStoreFactory
import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import ge.yet.game.miniapp.testkit.MutableMiniAppStorage
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import ge.yet.game.miniapp.testkit.NoopMiniAppAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class FruitMergeSessionComponentTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `restored terminal run opens Result and committed new game returns to Playing`() = runTest {
        val storage = MutableMiniAppStorage()
        val persistence = FruitMergePersistence(storage)
        persistence.checkpoint(FruitMergeState(runOrdinal = 7L, phase = RunPhase.RESULT))
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
        val result = assertIs<FruitMergeSessionComponent.Child.Result>(component.stack.value.active.instance)

        result.component.newGame()
        advanceUntilIdle()

        assertIs<FruitMergeSessionComponent.Child.Playing>(component.stack.value.active.instance)
        lifecycle.destroy()
    }

    @Test
    fun `first accepted tap advances tutorial and skip persists completion`() = runTest {
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
        val playing = assertIs<FruitMergeSessionComponent.Child.Playing>(component.stack.value.active.instance)

        assertIs<TutorialStep.Tap>(playing.component.model.value.tutorialStep)
        playing.component.drop(dragged = false)
        advanceUntilIdle()
        assertIs<TutorialStep.Drag>(playing.component.model.value.tutorialStep)
        playing.component.skipTutorial()
        advanceUntilIdle()

        kotlin.test.assertEquals(null, playing.component.model.value.tutorialStep)
        kotlin.test.assertTrue(FruitMergePersistence(storage).isTutorialSeen())
        lifecycle.destroy()
    }
}
