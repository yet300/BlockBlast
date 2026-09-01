package ge.yet.game.fruitmerge.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RandomStateTest {
    @Test
    fun `same seed produces the same bounded sequence`() {
        fun sequence(seed: Long): List<Int> {
            var state = RandomState(seed)
            return List(32) {
                val next = state.nextInt()
                state = next.state
                next.value
            }
        }

        val first = sequence(7)
        assertEquals(first, sequence(7))
        assertTrue(first.all { it in 0 until Int.MAX_VALUE })
    }
}
