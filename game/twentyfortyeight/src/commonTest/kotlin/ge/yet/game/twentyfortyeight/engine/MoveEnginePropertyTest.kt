package ge.yet.game.twentyfortyeight.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MoveEnginePropertyTest {
    private val engine = MoveEngine(SpawnPolicy())

    @Test
    fun `generated moves preserve engine invariants`() {
        val generator = ValidBoardGenerator(seed = 0x2048uL)
        repeat(10_000) { ordinal ->
            val input = generator.nextMoveInput()
            val direction = Direction.entries[ordinal % Direction.entries.size]
            val first = engine.apply(input, direction, transitionId = ordinal.toLong())
            val second = engine.apply(input, direction, transitionId = ordinal.toLong())

            assertEquals(first.valueProjection(), second.valueProjection(), "case=$ordinal")
            when (first) {
                is MoveResult.Unchanged -> {
                    assertEquals(input.board, first.board)
                    assertEquals(input.score, first.score)
                    assertEquals(input.rng, first.rng)
                }
                is MoveResult.Changed -> {
                    assertTrue(first.finalBoard.values().filterNotNull().all(::isSupportedPowerOfTwo))
                    assertEquals(input.board.tileSum(), first.afterMoveBoard.tileSum())
                    assertEquals(
                        first.afterMoveBoard.tileSum() + first.spawn.value.value,
                        first.finalBoard.tileSum(),
                    )
                    assertEquals(first.merges.sumOf { it.resultValue.value }, first.scoreDelta)
                    assertEquals(first.motions.size, first.motions.map { it.sourceId }.toSet().size)
                    assertEquals(
                        GameRules.legalDirections(first.finalBoard).isEmpty(),
                        first.gameOver == GameOverTransition.Entered,
                    )
                    assertEquals(
                        input.board.values().count { it != null } - first.merges.size,
                        first.afterMoveBoard.values().count { it != null },
                    )
                    assertEquals(
                        first.afterMoveBoard.values().count { it != null } + 1,
                        first.finalBoard.values().count { it != null },
                    )
                    assertEquals(
                        first.finalBoard.tiles.filterNotNull().size,
                        first.finalBoard.tiles.filterNotNull().map { it.id }.toSet().size,
                    )
                }
                is MoveResult.Failed -> error("Generated bounded inputs must not overflow: ${first.reason}")
            }
        }
    }

    @Test
    fun `eight maximal merge results fail instead of overflowing score delta`() {
        val value = TileValue.MAX_MERGE_INPUT
        val board = runtimeBoardOf(
            value, value, value, value,
            value, value, value, value,
            value, value, value, value,
            value, value, value, value,
        )

        assertIs<MoveResult.Failed>(
            engine.apply(
                MoveInput(board, 0L, RngState.fromBits(1uL), nextTileId = 17L),
                Direction.Left,
                transitionId = 1L,
            ),
        )
    }
}

private class ValidBoardGenerator(seed: ULong) {
    private var state = seed

    fun nextMoveInput(): MoveInput {
        val values = List(Board.CELL_COUNT) {
            when (nextInt(6)) {
                0, 1 -> null
                else -> 1L shl (nextInt(11) + 1)
            }
        }
        val board = RuntimeBoard.restore(Board.fromValues(values)).first
        return MoveInput(
            board = board,
            score = nextInt(100_000).toLong(),
            rng = RngState.fromBits(nextULong()),
            nextTileId = board.tiles.count { it != null }.toLong() + 1L,
            victoryAlreadyReached = nextInt(2) == 0,
        )
    }

    private fun nextInt(bound: Int): Int = (nextULong() % bound.toULong()).toInt()

    private fun nextULong(): ULong {
        state = state * 6_364_136_223_846_793_005uL + 1_442_695_040_888_963_407uL
        return state
    }
}

private fun MoveResult.valueProjection(): Any = when (this) {
    is MoveResult.Unchanged -> listOf(direction, board.values(), score, rng)
    is MoveResult.Changed -> listOf(
        transitionId,
        direction,
        beforeBoard.values(),
        afterMoveBoard.values(),
        finalBoard.values(),
        motions,
        merges,
        scoreBefore,
        scoreDelta,
        scoreAfter,
        spawn,
        rngBefore,
        rngAfter,
        victory,
        gameOver,
    )
    is MoveResult.Failed -> listOf(direction, reason)
}

private fun RuntimeBoard.tileSum(): Long = values().sumOf { it ?: 0L }

private fun isSupportedPowerOfTwo(value: Long): Boolean =
    value > 0L && value <= TileValue.MAX_VALUE && value and (value - 1L) == 0L
