package ge.yet.game.fruitmerge

import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import kotlin.test.Test
import kotlin.test.assertEquals

class FruitmergeComponentTest {
    @Test
    fun `component delegates typed actions to the engine`() {
        val lifecycle = MiniAppLifecycleHarness()
        val component = DefaultFruitmergeComponent(
            componentContext = lifecycle.componentContext,
            engine = IncrementingFruitmergeGameEngine,
        )

        component.dispatch(FruitmergeGameAction.Tick)

        assertEquals(1, component.model.value.state.score)
        lifecycle.destroy()
    }
}

private object IncrementingFruitmergeGameEngine : FruitmergeGameEngine {
    override fun reduce(
        state: FruitmergeGameState,
        action: FruitmergeGameAction,
    ): FruitmergeGameState = when (action) {
        FruitmergeGameAction.Reset -> FruitmergeGameState()
        FruitmergeGameAction.Tick -> state.copy(score = state.score + 1)
    }
}
