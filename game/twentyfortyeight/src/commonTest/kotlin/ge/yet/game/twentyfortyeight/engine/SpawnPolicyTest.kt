package ge.yet.game.twentyfortyeight.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpawnPolicyTest {
    @Test
    fun `spawn draws row major location before value`() {
        val calls = mutableListOf<Int>()
        val results = ArrayDeque(listOf(1, 0))
        val policy = SpawnPolicy { state, bound ->
            calls += bound
            results.removeFirst() to RngState.fromBits(state.bits + 1uL)
        }
        val board = Board.fromValues(listOf(2L, null, null, 4L) + List(12) { null })

        val spawned = policy.spawn(board, RngState.fromBits(0uL))

        assertEquals(listOf(14, 10), calls)
        assertEquals(Position(0, 2), spawned?.position)
        assertEquals(TileValue(4L), spawned?.value)
        assertEquals(RngState.fromBits(2uL), spawned?.rngAfter)
    }

    @Test
    fun `roll zero spawns four and all other rolls spawn two`() {
        (0 until 10).forEach { roll ->
            val results = ArrayDeque(listOf(0, roll))
            val policy = SpawnPolicy { state, _ ->
                results.removeFirst() to RngState.fromBits(state.bits + 1uL)
            }

            val spawned = policy.spawn(Board.empty(), RngState.fromBits(0uL))

            assertEquals(if (roll == 0) 4L else 2L, spawned?.value?.value, "roll=$roll")
        }
    }

    @Test
    fun `full board consumes no random draws`() {
        var calls = 0
        val policy = SpawnPolicy { state, _ ->
            calls += 1
            0 to state
        }
        val full = Board.fromValues(List(16) { 2L })

        assertNull(policy.spawn(full, RngState.fromBits(0uL)))
        assertEquals(0, calls)
    }

    @Test
    fun `new board performs two sequential deterministic spawns`() {
        val policy = SpawnPolicy()
        val initial = RngState.fromBits(0x2048uL)

        val (first, firstState) = policy.newBoard(initial)
        val (second, secondState) = policy.newBoard(initial)

        assertEquals(2, first.values.count { it != null })
        assertEquals(first, second)
        assertEquals(firstState, secondState)
        assertEquals(first.values.sumOf { it ?: 0L } in setOf(4L, 6L, 8L), true)
    }
}
