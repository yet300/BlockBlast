package ge.yet.game.fruitmerge.store

import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.game.fruitmerge.TestFruitMergeRules
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
}
