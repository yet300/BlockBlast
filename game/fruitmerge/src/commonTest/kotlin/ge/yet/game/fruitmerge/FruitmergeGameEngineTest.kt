package ge.yet.game.fruitmerge

import kotlin.test.Test
import kotlin.test.assertEquals

class FruitmergeGameEngineTest {
    @Test
    fun `reset returns the initial state`() {
        val state = FruitmergeGameState(score = 42, isGameOver = true)

        assertEquals(
            FruitmergeGameState(),
            DefaultFruitmergeGameEngine.reduce(state, FruitmergeGameAction.Reset),
        )
    }

    @Test
    fun `tick is an explicit placeholder for game rules`() {
        val state = FruitmergeGameState(score = 7)

        assertEquals(
            state,
            DefaultFruitmergeGameEngine.reduce(state, FruitmergeGameAction.Tick),
        )
    }
}
