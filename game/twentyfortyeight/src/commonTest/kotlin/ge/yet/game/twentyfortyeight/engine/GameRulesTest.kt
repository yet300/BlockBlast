package ge.yet.game.twentyfortyeight.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class GameRulesTest {
    @Test
    fun `empty board has no legal direction`() {
        assertEquals(emptySet(), GameRules.legalDirections(runtimeBoardOf(*Array(16) { null })))
    }

    @Test
    fun `a centered tile can move in every direction`() {
        val board = runtimeBoardOf(
            null, null, null, null,
            null, 2L, null, null,
            null, null, null, null,
            null, null, null, null,
        )

        assertEquals(Direction.entries.toSet(), GameRules.legalDirections(board))
    }

    @Test
    fun `full checkerboard has no legal direction`() {
        val board = runtimeBoardOf(
            2L, 4L, 2L, 4L,
            4L, 2L, 4L, 2L,
            2L, 4L, 2L, 4L,
            4L, 2L, 4L, 2L,
        )

        assertEquals(emptySet(), GameRules.legalDirections(board))
    }

    @Test
    fun `horizontal pair makes only horizontal directions legal`() {
        val board = runtimeBoardOf(
            2L, 2L, 4L, 8L,
            16L, 32L, 64L, 128L,
            256L, 512L, 1024L, 2048L,
            4096L, 8192L, 16384L, 32768L,
        )

        assertEquals(setOf(Direction.Left, Direction.Right), GameRules.legalDirections(board))
    }

    @Test
    fun `equal ceiling tiles are not a legal merge`() {
        val max = TileValue.MAX_VALUE
        val board = runtimeBoardOf(
            max, max, 2L, 4L,
            8L, 16L, 32L, 64L,
            128L, 256L, 512L, 1024L,
            2048L, 4096L, 8192L, 16384L,
        )

        assertEquals(emptySet(), GameRules.legalDirections(board))
    }
}
