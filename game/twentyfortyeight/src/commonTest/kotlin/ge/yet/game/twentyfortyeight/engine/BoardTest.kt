package ge.yet.game.twentyfortyeight.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class BoardTest {
    @Test
    fun `board accepts exactly sixteen nullable checked powers of two`() {
        val values = List(16) { index ->
            if (index % 2 == 0) 2L shl index.coerceAtMost(10) else null
        }

        val board = Board.fromValues(values)

        assertEquals(values, board.values)
        assertEquals(TileValue(2L), board[Position(0, 0)])
        assertNull(board[Position(0, 1)])
        assertFailsWith<IllegalArgumentException> { Board.fromValues(List(15) { null }) }
        assertFailsWith<IllegalArgumentException> { Board.fromValues(List(17) { null }) }
        listOf(0L, -2L, 3L, Long.MAX_VALUE).forEach { invalid ->
            assertFailsWith<IllegalArgumentException>("value=$invalid") {
                Board.fromValues(listOf(invalid) + List(15) { null })
            }
        }
    }

    @Test
    fun `tile ceiling is two to the sixty second power`() {
        val ceiling = 1L shl 62

        assertEquals(ceiling, TileValue(ceiling).value)
        assertFailsWith<IllegalArgumentException> { TileValue(Long.MIN_VALUE) }
    }

    @Test
    fun `positions are row major and reject coordinates outside four by four`() {
        assertEquals(0, Position(0, 0).index)
        assertEquals(6, Position(1, 2).index)
        assertEquals(15, Position(3, 3).index)
        assertFailsWith<IllegalArgumentException> { Position(-1, 0) }
        assertFailsWith<IllegalArgumentException> { Position(0, 4) }
    }

    @Test
    fun `board reports empties maximum neighbors and checked sum`() {
        val board = Board.fromValues(
            listOf(
                2L, 4L, null, null,
                8L, 16L, null, null,
                null, null, null, null,
                null, null, null, 32L,
            ),
        )

        assertEquals(11, board.emptyPositions().size)
        assertEquals(Position(0, 2), board.emptyPositions().first())
        assertEquals(TileValue(32L), board.maxTile())
        assertEquals(62L, board.sum())
        assertEquals(
            setOf(Position(0, 1), Position(1, 0)),
            board.neighbors(Position(0, 0)).toSet(),
        )
        assertEquals(
            setOf(Position(2, 3), Position(3, 2)),
            board.neighbors(Position(3, 3)).toSet(),
        )
    }

    @Test
    fun `board sum rejects Long overflow`() {
        val board = Board.fromValues(listOf(1L shl 62, 1L shl 62) + List(14) { null })

        assertFailsWith<ArithmeticException> { board.sum() }
    }

    @Test
    fun `board owns an immutable structural copy`() {
        val source = MutableList<Long?>(16) { null }
        source[0] = 2L
        val board = Board.fromValues(source)
        source[0] = 4L

        assertEquals(2L, board.values[0])
        assertEquals(board, Board.fromValues(listOf(2L) + List(15) { null }))
        assertEquals(board.hashCode(), Board.fromValues(listOf(2L) + List(15) { null }).hashCode())
        assertNotEquals(board, Board.empty())
    }

    @Test
    fun `runtime restore assigns stable row major identities`() {
        val board = Board.fromValues(listOf(2L, null, 4L) + List(13) { null })

        val (runtime, nextId) = RuntimeBoard.restore(board)

        assertEquals(TileId(1L), runtime.tiles[0]?.id)
        assertEquals(TileId(2L), runtime.tiles[2]?.id)
        assertEquals(3L, nextId)
        assertEquals(board, runtime.valueBoard())
    }
}
