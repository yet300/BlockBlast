package ge.yet.game.twentyfortyeight.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MoveEngineTest {
    @Test
    fun `left line regressions merge each source once`() {
        val cases = listOf(
            listOf(2L, 2L, 2L, 2L) to Pair(listOf(4L, 4L, null, null), 8L),
            listOf(2L, 2L, 4L, null) to Pair(listOf(4L, 4L, null, null), 4L),
            listOf(4L, 4L, 4L, null) to Pair(listOf(8L, 4L, null, null), 8L),
            listOf(2L, null, 2L, 2L) to Pair(listOf(4L, 2L, null, null), 4L),
        )

        cases.forEach { (input, expected) ->
            val result = reduceLineForTest(input)
            assertEquals(expected.first, result.values)
            assertEquals(expected.second, result.scoreDelta)
            assertEquals(result.sourceIds.size, result.sourceIds.toSet().size)
        }
    }

    @Test
    fun `changed move exposes stable identities and exact spawn`() {
        val before = runtimeBoardOf(
            2L, 2L, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
        )
        val engine = MoveEngine(fixedSpawnPolicy(position = Position(3, 3), valueRoll = 1, nextBits = 99uL))

        val changed = assertIs<MoveResult.Changed>(
            engine.apply(
                input = MoveInput(
                    board = before,
                    score = 10L,
                    rng = RngState.fromBits(7uL),
                    nextTileId = 3L,
                ),
                direction = Direction.Left,
                transitionId = 41L,
            ),
        )

        assertEquals(41L, changed.transitionId)
        assertEquals(listOf(1L, 2L), changed.merges.single().sourceIds.map(TileId::value))
        assertEquals(TileId(3L), changed.merges.single().resultId)
        assertEquals(Position(0, 0), changed.merges.single().target)
        assertEquals(4L, changed.merges.single().resultValue.value)
        assertEquals(listOf(1L, 2L), changed.motions.map { it.sourceId.value })
        assertTrue(changed.motions.all { it.outcomeId == TileId(3L) })
        assertEquals(4L, changed.scoreDelta)
        assertEquals(14L, changed.scoreAfter)
        assertEquals(2L, changed.spawn.value.value)
        assertEquals(Position(3, 3), changed.spawn.position)
        assertEquals(TileId(4L), changed.spawn.id)
        assertEquals(RngState.fromBits(7uL), changed.rngBefore)
        assertEquals(RngState.fromBits(99uL), changed.rngAfter)
    }

    @Test
    fun `unchanged move preserves exact input and consumes no randomness`() {
        var draws = 0
        val engine = MoveEngine(
            SpawnPolicy { state, _ ->
                draws += 1
                0 to state
            },
        )
        val board = runtimeBoardOf(
            2L, 4L, 8L, 16L,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
        )

        val result = assertIs<MoveResult.Unchanged>(
            engine.apply(
                MoveInput(board, score = 100L, rng = RngState.fromBits(5uL), nextTileId = 5L),
                Direction.Left,
                transitionId = 1L,
            ),
        )

        assertSame(board, result.board)
        assertEquals(100L, result.score)
        assertEquals(RngState.fromBits(5uL), result.rng)
        assertEquals(0, draws)
    }

    @Test
    fun `up right and down map travel coordinates exactly`() {
        val vertical = runtimeBoardOf(
            null, 2L, null, null,
            null, 2L, null, null,
            null, null, null, null,
            null, null, null, null,
        )
        val horizontal = runtimeBoardOf(
            2L, 2L, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
        )
        val engine = MoveEngine(fixedSpawnPolicy(Position(3, 3), valueRoll = 1, nextBits = 9uL))

        val up = assertIs<MoveResult.Changed>(
            engine.apply(MoveInput(vertical, 0L, RngState.fromBits(1uL), 3L), Direction.Up, 1L),
        )
        val right = assertIs<MoveResult.Changed>(
            engine.apply(MoveInput(horizontal, 0L, RngState.fromBits(1uL), 3L), Direction.Right, 2L),
        )
        val down = assertIs<MoveResult.Changed>(
            engine.apply(MoveInput(vertical, 0L, RngState.fromBits(1uL), 3L), Direction.Down, 3L),
        )

        assertEquals(4L, up.afterMoveBoard[Position(0, 1)]?.value?.value)
        assertEquals(4L, right.afterMoveBoard[Position(0, 3)]?.value?.value)
        assertEquals(4L, down.afterMoveBoard[Position(3, 1)]?.value?.value)
    }

    @Test
    fun `score overflow returns a typed failure before spawning`() {
        var draws = 0
        val engine = MoveEngine(
            SpawnPolicy { state, _ ->
                draws += 1
                0 to state
            },
        )
        val board = runtimeBoardOf(
            2L, 2L, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
        )

        val result = engine.apply(
            MoveInput(board, Long.MAX_VALUE, RngState.fromBits(0uL), 3L),
            Direction.Left,
            transitionId = 1L,
        )

        assertEquals(MoveResult.Failed(Direction.Left, MoveFailure.ScoreOverflow), result)
        assertEquals(0, draws)
    }

    @Test
    fun `move input rejects next tile ID at or below the board maximum`() {
        val board = runtimeBoardWithIds(
            7L to 2L, null, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
        )

        listOf(7L, 6L).forEach { invalidNextTileId ->
            assertFailsWith<IllegalArgumentException> {
                MoveInput(board, 0L, RngState.fromBits(1uL), nextTileId = invalidNextTileId)
            }
        }
    }

    @Test
    fun `identity range that cannot hold every merge fails before randomness`() {
        assertIdentityOverflowBeforeRandomness(
            board = runtimeBoardOf(
                2L, 2L, 2L, 2L,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
            ),
            nextTileId = Long.MAX_VALUE,
        )
    }

    @Test
    fun `identity range that cannot hold spawn fails before randomness`() {
        assertIdentityOverflowBeforeRandomness(
            board = runtimeBoardOf(
                2L, 2L, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
            ),
            nextTileId = Long.MAX_VALUE,
        )
    }

    @Test
    fun `spawn at Long max without a following ID is identity overflow`() {
        assertIdentityOverflowBeforeRandomness(
            board = runtimeBoardOf(
                null, 2L, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
            ),
            nextTileId = Long.MAX_VALUE,
        )
    }

    @Test
    fun `first 2048 merge emits victory exactly once`() {
        val board = runtimeBoardOf(
            1024L, 1024L, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
        )

        fun move(victoryAlreadyReached: Boolean): MoveResult.Changed = assertIs(
            MoveEngine(fixedSpawnPolicy(Position(3, 3), valueRoll = 1, nextBits = 9uL)).apply(
                MoveInput(
                    board = board,
                    score = 0L,
                    rng = RngState.fromBits(1uL),
                    nextTileId = 3L,
                    victoryAlreadyReached = victoryAlreadyReached,
                ),
                Direction.Left,
                transitionId = 1L,
            ),
        )

        assertEquals(VictoryTransition.FirstReached, move(false).victory)
        assertEquals(VictoryTransition.None, move(true).victory)
    }

    @Test
    fun `spawn that fills the final cell can enter game over`() {
        val board = runtimeBoardOf(
            2L, 4L, 2L, 4L,
            4L, 2L, 4L, 2L,
            2L, 4L, 2L, 4L,
            null, 4L, 2L, 4L,
        )
        val changed = assertIs<MoveResult.Changed>(
            MoveEngine(fixedSpawnPolicy(Position(3, 3), valueRoll = 1, nextBits = 9uL)).apply(
                MoveInput(board, 0L, RngState.fromBits(1uL), nextTileId = 16L),
                Direction.Left,
                transitionId = 1L,
            ),
        )

        assertEquals(GameOverTransition.Entered, changed.gameOver)
        assertEquals(emptySet(), GameRules.legalDirections(changed.finalBoard))
    }

    @Test
    fun `ceiling tiles never merge`() {
        val max = TileValue.MAX_VALUE
        val result = reduceLineForTest(listOf(max, max, null, null))

        assertEquals(listOf(max, max, null, null), result.values)
        assertEquals(0L, result.scoreDelta)
    }

    private fun assertIdentityOverflowBeforeRandomness(
        board: RuntimeBoard,
        nextTileId: Long,
    ) {
        var draws = 0
        val engine = MoveEngine(
            SpawnPolicy { state, _ ->
                draws += 1
                0 to state
            },
        )
        val input = MoveInput(
            board = board,
            score = 0L,
            rng = RngState.fromBits(1uL),
            nextTileId = nextTileId,
        )

        val result = engine.apply(input, Direction.Left, transitionId = 1L)

        assertEquals(MoveResult.Failed(Direction.Left, MoveFailure.IdentityOverflow), result)
        assertEquals(0, draws)
        assertSame(board, input.board)
        assertEquals(board, input.board)
    }
}

internal fun runtimeBoardOf(vararg values: Long?): RuntimeBoard {
    require(values.size == Board.CELL_COUNT)
    return RuntimeBoard.restore(Board.fromValues(values.toList())).first
}

internal fun runtimeBoardWithIds(vararg tiles: Pair<Long, Long>?): RuntimeBoard {
    require(tiles.size == Board.CELL_COUNT)
    return RuntimeBoard.fromTiles(
        tiles.map { tile ->
            tile?.let { (id, value) -> RuntimeTile(TileId(id), TileValue(value)) }
        },
    )
}

internal fun fixedSpawnPolicy(
    position: Position,
    valueRoll: Int,
    nextBits: ULong,
): SpawnPolicy {
    require(position == Position(3, 3)) { "This fixture selects the last row-major free cell" }
    return SpawnPolicy { state, bound ->
        if (bound == 10) {
            valueRoll to RngState.fromBits(nextBits)
        } else {
            (bound - 1) to state
        }
    }
}
