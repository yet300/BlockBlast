package ge.yet.game.fruitmerge.store

import com.arkivanov.mvikotlin.core.rx.observer
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.game.fruitmerge.TestFruitMergeRules
import ge.yet.game.fruitmerge.engine.FruitBody
import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.engine.FruitMergeEngine
import ge.yet.game.fruitmerge.engine.FruitMergeState
import ge.yet.game.fruitmerge.engine.RunPhase
import ge.yet.game.fruitmerge.engine.Vec2
import ge.yet.game.fruitmerge.persistence.FruitMergePersistence
import ge.yet.game.miniapp.testkit.MutableMiniAppStorage
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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FruitMergeStoreTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `frame gap executes at most three fixed steps`() = runTest {
        val rules = TestFruitMergeRules()
        val store = FruitMergeStoreFactory(
            storeFactory = DefaultStoreFactory(),
            rules = rules,
            persistence = FruitMergePersistence(MutableMiniAppStorage()),
        ).create()
        advanceUntilIdle()

        store.accept(FruitMergeStore.Intent.Frame(1f))

        assertEquals(3, rules.stepCalls)
        assertTrue(store.state.initialized)
        store.dispose()
    }

    @Test
    fun `inactive store ignores frame work`() = runTest {
        val rules = TestFruitMergeRules()
        val store = FruitMergeStoreFactory(
            storeFactory = DefaultStoreFactory(),
            rules = rules,
            persistence = FruitMergePersistence(MutableMiniAppStorage()),
        ).create()
        advanceUntilIdle()
        store.accept(FruitMergeStore.Intent.VisibilityChanged(active = false))

        store.accept(FruitMergeStore.Intent.Frame(1f))

        assertEquals(0, rules.stepCalls)
        store.dispose()
    }

    @Test
    fun `accepted drop publishes once while cooldown rejection stays silent`() = runTest {
        val store = createStore()
        val labels = mutableListOf<FruitMergeStore.Label>()
        val subscription = store.labels(observer(onNext = labels::add))
        advanceUntilIdle()

        store.accept(FruitMergeStore.Intent.Drop)
        store.accept(FruitMergeStore.Intent.Drop)

        assertEquals(
            listOf<FruitMergeStore.Label>(
                FruitMergeStore.Label.DropReleased(FruitLevel.BLUEBERRY),
            ),
            labels,
        )
        subscription.dispose()
        store.dispose()
    }

    @Test
    fun `frame publishes the exact level created by a merge`() = runTest {
        val rules = TestFruitMergeRules()
        val store = createStore(rules)
        val labels = mutableListOf<FruitMergeStore.Label>()
        val subscription = store.labels(observer(onNext = labels::add))
        advanceUntilIdle()
        rules.nextStepState = FruitMergeState(
            bodies = listOf(
                FruitBody(
                    id = 1L,
                    level = FruitLevel.RASPBERRY,
                    position = Vec2(0.5f, 0.5f),
                ),
            ),
            nextBodyId = 2L,
            score = FruitLevel.RASPBERRY.mergeScore,
        )

        store.accept(FruitMergeStore.Intent.Frame(1f / 60f))

        assertIs<FruitMergeStore.Label.MergeResolved>(labels.single())
        assertEquals(FruitLevel.RASPBERRY, (labels.single() as FruitMergeStore.Label.MergeResolved).level)
        assertEquals(Vec2(0.5f, 0.5f), (labels.single() as FruitMergeStore.Label.MergeResolved).position)
        subscription.dispose()
        store.dispose()
    }

    @Test
    fun `accepted shake publishes once while empty board rejection stays silent`() = runTest {
        val store = createStore()
        val labels = mutableListOf<FruitMergeStore.Label>()
        val subscription = store.labels(observer(onNext = labels::add))
        advanceUntilIdle()

        store.accept(FruitMergeStore.Intent.FreeShake)
        store.accept(FruitMergeStore.Intent.Drop)
        labels.clear()
        store.accept(FruitMergeStore.Intent.FreeShake)

        assertEquals(
            listOf<FruitMergeStore.Label>(FruitMergeStore.Label.ShakeStarted),
            labels,
        )
        subscription.dispose()
        store.dispose()
    }

    @Test
    fun `duplicate shake while active publishes once and consumes once`() = runTest {
        val store = createStore()
        val labels = mutableListOf<FruitMergeStore.Label>()
        val subscription = store.labels(observer(onNext = labels::add))
        advanceUntilIdle()
        store.accept(FruitMergeStore.Intent.Drop)
        labels.clear()

        store.accept(FruitMergeStore.Intent.FreeShake)
        store.accept(FruitMergeStore.Intent.FreeShake)

        assertEquals(listOf<FruitMergeStore.Label>(FruitMergeStore.Label.ShakeStarted), labels)
        assertEquals(FruitMergeState.FREE_SHAKE_COUNT - 1, store.state.game.freeShakes)
        assertTrue(store.state.game.shakeStepsRemaining > 0)
        subscription.dispose()
        store.dispose()
    }

    @Test
    fun `accepted clear publishes only its committed effect`() = runTest {
        val store = createStore()
        val labels = mutableListOf<FruitMergeStore.Label>()
        val subscription = store.labels(observer(onNext = labels::add))
        advanceUntilIdle()
        store.accept(FruitMergeStore.Intent.Drop)
        labels.clear()

        store.accept(FruitMergeStore.Intent.BeginFreeClear)
        store.accept(FruitMergeStore.Intent.ClearBody(id = 1L, paid = false))

        assertEquals(
            listOf<FruitMergeStore.Label>(
                FruitMergeStore.Label.ClearApplied(
                    level = FruitLevel.BLUEBERRY,
                    position = Vec2(0.5f, 0.08f),
                ),
            ),
            labels,
        )
        subscription.dispose()
        store.dispose()
    }

    @Test
    fun `landing danger and shake pulses publish only on committed edges`() = runTest {
        val rules = TestFruitMergeRules()
        val store = createStore(rules)
        val labels = mutableListOf<FruitMergeStore.Label>()
        val subscription = store.labels(observer(onNext = labels::add))
        advanceUntilIdle()
        store.accept(FruitMergeStore.Intent.Drop)
        labels.clear()

        val airborne = store.state.game.bodies.single()
        rules.nextStepState = store.state.game.copy(
            bodies = listOf(airborne.copy(hasJoinedPile = true, position = Vec2(0.5f, 0.4f))),
            dangerSeconds = 0.1f,
        )
        store.accept(FruitMergeStore.Intent.Frame(1f / 60f))
        rules.nextStepState = store.state.game.copy(dangerSeconds = 0.2f)
        store.accept(FruitMergeStore.Intent.Frame(1f / 60f))

        assertEquals(
            listOf<FruitMergeStore.Label>(
                FruitMergeStore.Label.FruitLanded(FruitLevel.BLUEBERRY, Vec2(0.5f, 0.4f)),
                FruitMergeStore.Label.DangerEntered,
            ),
            labels,
        )

        labels.clear()
        store.accept(FruitMergeStore.Intent.FreeShake)
        store.accept(FruitMergeStore.Intent.Frame(1f / 60f))
        repeat(FruitMergeEngine.SHAKE_IMPULSE_INTERVAL_STEPS) {
            store.accept(FruitMergeStore.Intent.Frame(1f / 60f))
        }
        val pulses = labels.filterIsInstance<FruitMergeStore.Label.ShakePulse>()
        assertEquals(listOf(0, 1), pulses.map(FruitMergeStore.Label.ShakePulse::index))

        subscription.dispose()
        store.dispose()
    }

    @Test
    fun `playing to result transition publishes exactly once`() = runTest {
        val rules = TestFruitMergeRules()
        val store = createStore(rules)
        val labels = mutableListOf<FruitMergeStore.Label>()
        val subscription = store.labels(observer(onNext = labels::add))
        advanceUntilIdle()
        rules.nextStepState = FruitMergeState(phase = RunPhase.RESULT)

        store.accept(FruitMergeStore.Intent.Frame(1f / 60f))
        store.accept(FruitMergeStore.Intent.Frame(1f / 60f))

        assertEquals(
            listOf<FruitMergeStore.Label>(FruitMergeStore.Label.ResultReached),
            labels,
        )
        subscription.dispose()
        store.dispose()
    }

    private fun createStore(
        rules: TestFruitMergeRules = TestFruitMergeRules(),
    ): FruitMergeStore = FruitMergeStoreFactory(
        storeFactory = DefaultStoreFactory(),
        rules = rules,
        persistence = FruitMergePersistence(MutableMiniAppStorage()),
    ).create()
}
