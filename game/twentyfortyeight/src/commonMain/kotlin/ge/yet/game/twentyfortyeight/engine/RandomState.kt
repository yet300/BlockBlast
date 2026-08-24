package ge.yet.game.twentyfortyeight.engine

internal data class RngState(
    val algorithm: String,
    val stateHex: String,
) {
    init {
        require(algorithm == ALGORITHM) { "Unsupported random algorithm: $algorithm" }
        require(STATE_PATTERN.matches(stateHex)) {
            "Random state must be exactly 16 lowercase hexadecimal characters: $stateHex"
        }
    }

    val bits: ULong
        get() = stateHex.toULong(radix = 16)

    companion object {
        const val ALGORITHM: String = "splitmix64-v1"
        private val STATE_PATTERN = Regex("[0-9a-f]{16}")

        fun fromBits(bits: ULong): RngState = RngState(
            algorithm = ALGORITHM,
            stateHex = bits.toString(radix = 16).padStart(length = 16, padChar = '0'),
        )
    }
}

internal data class RandomDraw(
    val value: Long,
    val next: RngState,
)

internal object SplitMix64 {
    private const val GAMMA: ULong = 0x9E37_79B9_7F4A_7C15uL
    private const val MIX_1: ULong = 0xBF58_476D_1CE4_E5B9uL
    private const val MIX_2: ULong = 0x94D0_49BB_1331_11EBuL

    fun next(state: RngState): RandomDraw {
        val nextBits = state.bits + GAMMA
        var mixed = nextBits
        mixed = (mixed xor (mixed shr 30)) * MIX_1
        mixed = (mixed xor (mixed shr 27)) * MIX_2
        mixed = mixed xor (mixed shr 31)
        return RandomDraw(
            value = mixed.toLong(),
            next = RngState.fromBits(nextBits),
        )
    }

    fun nextInt(state: RngState, bound: Int): Pair<Int, RngState> {
        require(bound > 0) { "Bound must be positive: $bound" }
        var current = state
        val value = unbiasedBoundedInt(bound) {
            val draw = next(current)
            current = draw.next
            draw.value.toULong()
        }
        return value to current
    }
}

internal fun unbiasedBoundedInt(
    bound: Int,
    draw: () -> ULong,
): Int {
    require(bound > 0) { "Bound must be positive: $bound" }
    val rangeSize = 1uL shl 63
    val unsignedBound = bound.toULong()
    val limit = rangeSize - rangeSize % unsignedBound
    while (true) {
        val candidate = draw() shr 1
        if (candidate < limit) return (candidate % unsignedBound).toInt()
    }
}
