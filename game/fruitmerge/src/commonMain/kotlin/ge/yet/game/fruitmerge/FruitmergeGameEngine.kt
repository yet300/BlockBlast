package ge.yet.game.fruitmerge

interface FruitmergeGameEngine {
    fun reduce(state: FruitmergeGameState, action: FruitmergeGameAction): FruitmergeGameState
}

internal object DefaultFruitmergeGameEngine : FruitmergeGameEngine {
    override fun reduce(
        state: FruitmergeGameState,
        action: FruitmergeGameAction,
    ): FruitmergeGameState = when (action) {
        FruitmergeGameAction.Reset -> FruitmergeGameState()
        FruitmergeGameAction.Tick -> state
    }
}
