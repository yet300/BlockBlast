package ge.yet.game.twentyfortyeight.engine

import kotlin.math.min
import kotlin.math.roundToInt

internal data class AudioControls(
    val progress: Float,
    val danger: Float,
    val momentum: Float,
)

internal object AudioControlPolicy {
    fun from(state: GameState): AudioControls {
        val occupiedCells = state.board.tiles.count { it != null }
        val legalDirectionCount = GameRules.legalDirections(state.board).size
        val pairCount = equalAdjacentPairCount(state.board)
        val occupancy = occupiedCells.toFloat() / Board.CELL_COUNT.toFloat()
        val mobilityRisk = 1f - legalDirectionCount.toFloat() / Direction.entries.size.toFloat()
        val mergeOpportunity = min(pairCount, 4).toFloat() / 4f
        val mergePressure = occupancy * (1f - mergeOpportunity)
        val terminalPressure = terminalPressure(legalDirectionCount)
        val danger =
            0.45f * occupancy +
                0.30f * mobilityRisk +
                0.15f * mergePressure +
                0.10f * terminalPressure
        return AudioControls(
            progress = quantize(progress(state.board)),
            danger = quantize(danger),
            momentum = quantize(state.momentumStreak.coerceAtMost(MAX_MOMENTUM).toFloat() / MAX_MOMENTUM),
        )
    }

    fun terminalPressure(legalDirectionCount: Int): Float {
        require(legalDirectionCount in 0..Direction.entries.size) {
            "Legal-direction count must be in 0..${Direction.entries.size}: $legalDirectionCount"
        }
        return when (legalDirectionCount) {
            0 -> 1f
            1 -> 0.8f
            2 -> 0.4f
            3 -> 0.15f
            else -> 0f
        }
    }

    fun quantize(value: Float): Float =
        (value.coerceIn(0f, 1f) * QUANTIZATION_STEPS).roundToInt() / QUANTIZATION_STEPS

    fun equalAdjacentPairCount(board: RuntimeBoard): Int {
        var pairs = 0
        repeat(Board.SIZE) { row ->
            repeat(Board.SIZE) { column ->
                val position = Position(row, column)
                val tile = board[position]
                if (tile != null) {
                    if (
                        column < Board.SIZE - 1 &&
                        board[Position(row, column + 1)]?.value == tile.value &&
                        tile.value.value <= TileValue.MAX_MERGE_INPUT
                    ) {
                        pairs += 1
                    }
                    if (
                        row < Board.SIZE - 1 &&
                        board[Position(row + 1, column)]?.value == tile.value &&
                        tile.value.value <= TileValue.MAX_MERGE_INPUT
                    ) {
                        pairs += 1
                    }
                }
            }
        }
        return pairs
    }

    private fun progress(board: RuntimeBoard): Float {
        var value = board.values().filterNotNull().maxOrNull() ?: return 0f
        var exponent = 0
        while (value > 1L) {
            value = value shr 1
            exponent += 1
        }
        return ((exponent - 1).toFloat() / 10f).coerceIn(0f, 1f)
    }

    private const val MAX_MOMENTUM: Int = 6
    private const val QUANTIZATION_STEPS: Float = 32f
}
