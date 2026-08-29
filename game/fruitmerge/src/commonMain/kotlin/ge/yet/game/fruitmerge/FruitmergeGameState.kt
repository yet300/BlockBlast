package ge.yet.game.fruitmerge

data class FruitmergeGameState(
    val score: Int = 0,
    val isGameOver: Boolean = false,
)

sealed interface FruitmergeGameAction {
    data object Reset : FruitmergeGameAction
    data object Tick : FruitmergeGameAction
}
