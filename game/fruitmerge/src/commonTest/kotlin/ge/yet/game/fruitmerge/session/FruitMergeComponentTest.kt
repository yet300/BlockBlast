package ge.yet.game.fruitmerge.session

import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.game.fruitmerge.TestFruitMergeRules
import ge.yet.game.fruitmerge.engine.FruitBody
import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.engine.FruitMergeState
import ge.yet.game.fruitmerge.engine.TargetingMode
import ge.yet.game.fruitmerge.engine.Vec2
import ge.yet.game.fruitmerge.persistence.FruitMergePersistence
import ge.yet.game.fruitmerge.store.FruitMergeStoreFactory
import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import ge.yet.game.miniapp.testkit.MutableMiniAppStorage
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class FruitMergeComponentTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `stale paid action token cannot mutate a new run`() = runTest {
        val harness = componentHarness(FruitMergeState(freeClears = 0))
        val token = assertNotNull(harness.component.requestClearGate())

        harness.component.newGame()
        harness.component.completePaidAction(token)

        assertEquals(TargetingMode.NONE, harness.component.model.value.game.targetingMode)
        harness.lifecycle.destroy()
    }

    @Test
    fun `duplicate completion applies a paid action once`() = runTest {
        val rules = TestFruitMergeRules()
        val harness = componentHarness(
            initial = FruitMergeState(
                bodies = listOf(FruitBody(1, FruitLevel.APPLE, Vec2(0.5f, 0.8f))),
                nextBodyId = 2,
                freeShakes = 0,
            ),
            rules = rules,
        )
        val token = assertNotNull(harness.component.requestShakeGate())

        harness.component.completePaidAction(token)
        harness.component.completePaidAction(token)

        assertEquals(1, rules.paidShakeCalls)
        harness.lifecycle.destroy()
    }

    private suspend fun TestScope.componentHarness(
        initial: FruitMergeState,
        rules: TestFruitMergeRules = TestFruitMergeRules(),
    ): Harness {
        val storage = MutableMiniAppStorage()
        val persistence = FruitMergePersistence(storage)
        persistence.checkpoint(initial)
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val store = FruitMergeStoreFactory(
            storeFactory = DefaultStoreFactory(),
            rules = rules,
            persistence = persistence,
        ).create()
        val component = DefaultFruitMergeComponent(
            componentContext = lifecycle.componentContext,
            store = store,
            persistence = persistence,
            visibility = MutableMiniAppVisibilitySource(),
        )
        advanceUntilIdle()
        return Harness(component, lifecycle)
    }

    private data class Harness(
        val component: DefaultFruitMergeComponent,
        val lifecycle: MiniAppLifecycleHarness,
    )
}
