package ge.yet.game.fruitmerge.engine

import kotlin.jvm.JvmInline

data class RandomValue(
    val state: RandomState,
    val value: Int,
)

@JvmInline
value class RandomState(val bits: Long) {
    fun nextInt(): RandomValue {
        var nextBits = if (bits == 0L) NON_ZERO_SEED else bits
        nextBits = nextBits xor (nextBits shl 13)
        nextBits = nextBits xor (nextBits ushr 7)
        nextBits = nextBits xor (nextBits shl 17)
        return RandomValue(
            state = RandomState(nextBits),
            value = (nextBits ushr 33).toInt() and Int.MAX_VALUE,
        )
    }

    private companion object {
        val NON_ZERO_SEED: Long = 0x9E3779B97F4A7C15UL.toLong()
    }
}
