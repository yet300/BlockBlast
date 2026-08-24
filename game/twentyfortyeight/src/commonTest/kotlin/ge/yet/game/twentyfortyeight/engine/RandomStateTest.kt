package ge.yet.game.twentyfortyeight.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RandomStateTest {
    @Test
    fun `state is lowercase sixteen character hex`() {
        val state = RngState.fromBits(0x0123_4567_89AB_CDEFuL)

        assertEquals("0123456789abcdef", state.stateHex)
        assertEquals(0x0123_4567_89AB_CDEFuL, state.bits)
        assertEquals(state, RngState("splitmix64-v1", "0123456789abcdef"))
        assertFailsWith<IllegalArgumentException> { RngState("other", "0123456789abcdef") }
        assertFailsWith<IllegalArgumentException> { RngState("splitmix64-v1", "123") }
        assertFailsWith<IllegalArgumentException> { RngState("splitmix64-v1", "0123456789ABCDEF") }
    }

    @Test
    fun `SplitMix64 vectors from zero match the portable definition`() {
        var state = RngState.fromBits(0uL)
        val expected = listOf(
            "9e3779b97f4a7c15" to "e220a8397b1dcdaf",
            "3c6ef372fe94f82a" to "6e789e6aa1b965f4",
            "daa66d2c7ddf743f" to "06c45d188009454f",
            "78dde6e5fd29f054" to "f88bb8a8724c81ec",
        )

        expected.forEach { (nextState, value) ->
            val draw = SplitMix64.next(state)
            assertEquals(nextState, draw.next.stateHex)
            assertEquals(value, draw.value.toULong().toPaddedHex())
            state = draw.next
        }
    }

    @Test
    fun `bounded draw rejects the biased high tail`() {
        val rangeSize = 1uL shl 63
        val limit = rangeSize - (rangeSize % 10uL)
        val candidates = ArrayDeque(
            listOf(
                limit shl 1,
                6uL,
            ),
        )
        var calls = 0

        val result = unbiasedBoundedInt(bound = 10) {
            calls += 1
            candidates.removeFirst()
        }

        assertEquals(3, result)
        assertEquals(2, calls)
    }

    @Test
    fun `nextInt is deterministic and validates its bound`() {
        val state = RngState.fromBits(42uL)
        assertEquals(SplitMix64.nextInt(state, 7), SplitMix64.nextInt(state, 7))
        assertFailsWith<IllegalArgumentException> { SplitMix64.nextInt(state, 0) }
    }
}

private fun ULong.toPaddedHex(): String = toString(16).padStart(16, '0')
