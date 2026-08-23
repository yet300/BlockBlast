package ge.yet.game.miniapp.audio.internal.dsp

import ge.yet.game.miniapp.audio.NoiseColor

internal class NoiseState(seed: Long) {
    private var randomState = seed.takeUnless { it == 0L } ?: -7046029254386353131L
    internal var pink0 = 0.0
    internal var pink1 = 0.0
    internal var pink2 = 0.0
    internal var brown = 0.0

    internal fun nextWhite(): Double {
        var value = randomState
        value = value xor (value shl 13)
        value = value xor (value ushr 7)
        value = value xor (value shl 17)
        randomState = value
        val normalized = ((value ushr 40) and 0xFFFFFF).toDouble() / 0x7FFFFF - 1.0
        return normalized.coerceIn(-1.0, 1.0)
    }
}

internal fun renderNoise(
    color: NoiseColor,
    state: NoiseState,
    output: FloatArray,
    frameCount: Int,
    outputOffset: Int = 0,
) {
    require(frameCount >= 0 && outputOffset >= 0 && outputOffset + frameCount <= output.size)
    for (frame in 0 until frameCount) {
        output[outputOffset + frame] = nextNoiseSample(color, state)
    }
}

internal fun nextNoiseSample(color: NoiseColor, state: NoiseState): Float {
    val white = state.nextWhite()
    val sample = when (color) {
        NoiseColor.WHITE -> white
        NoiseColor.PINK -> {
            state.pink0 = 0.99765 * state.pink0 + white * 0.0990460
            state.pink1 = 0.96300 * state.pink1 + white * 0.2965164
            state.pink2 = 0.57000 * state.pink2 + white * 1.0526913
            (state.pink0 + state.pink1 + state.pink2 + white * 0.1848) * 0.2
        }
        NoiseColor.BROWN -> {
            state.brown = (state.brown * 0.995 + white * 0.02).coerceIn(-1.0, 1.0)
            state.brown
        }
    }
    return sample.takeIf { it.isFinite() }?.coerceIn(-1.0, 1.0)?.toFloat() ?: 0f
}
