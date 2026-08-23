package ge.yet.game.miniapp.audio.internal.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class BiquadCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
) {
    companion object {
        fun lowPass(sampleRate: Int, frequencyHz: Double, q: Double): BiquadCoefficients =
            create(sampleRate, frequencyHz, q, Kind.LOW_PASS)

        fun highPass(sampleRate: Int, frequencyHz: Double, q: Double): BiquadCoefficients =
            create(sampleRate, frequencyHz, q, Kind.HIGH_PASS)

        fun bandPass(sampleRate: Int, frequencyHz: Double, q: Double): BiquadCoefficients =
            create(sampleRate, frequencyHz, q, Kind.BAND_PASS)

        private fun create(sampleRate: Int, frequencyHz: Double, q: Double, kind: Kind): BiquadCoefficients {
            require(sampleRate > 0)
            val frequency = frequencyHz.takeIf { it.isFinite() }?.coerceIn(10.0, sampleRate * 0.45) ?: 10.0
            val safeQ = q.takeIf { it.isFinite() }?.coerceIn(0.1, 20.0) ?: 0.707
            val omega = 2.0 * PI * frequency / sampleRate
            val cosine = cos(omega)
            val alpha = sin(omega) / (2.0 * safeQ)
            val a0 = 1.0 + alpha
            val (b0, b1, b2) = when (kind) {
                Kind.LOW_PASS -> Triple((1.0 - cosine) / 2.0, 1.0 - cosine, (1.0 - cosine) / 2.0)
                Kind.HIGH_PASS -> Triple((1.0 + cosine) / 2.0, -(1.0 + cosine), (1.0 + cosine) / 2.0)
                Kind.BAND_PASS -> Triple(alpha, 0.0, -alpha)
            }
            return BiquadCoefficients(b0 / a0, b1 / a0, b2 / a0, -2.0 * cosine / a0, (1.0 - alpha) / a0)
        }
    }

    private enum class Kind { LOW_PASS, HIGH_PASS, BAND_PASS }
}

internal class BiquadState {
    internal var x1 = 0.0
    internal var x2 = 0.0
    internal var y1 = 0.0
    internal var y2 = 0.0
}

internal fun processBiquad(
    buffer: FloatArray,
    coefficients: BiquadCoefficients,
    state: BiquadState,
    frameCount: Int = buffer.size,
    offset: Int = 0,
) {
    require(frameCount >= 0 && offset >= 0 && offset + frameCount <= buffer.size)
    var x1 = state.x1
    var x2 = state.x2
    var y1 = state.y1
    var y2 = state.y2
    for (frame in 0 until frameCount) {
        val index = offset + frame
        val input = buffer[index].takeIf { it.isFinite() }?.toDouble() ?: 0.0
        val raw = coefficients.b0 * input + coefficients.b1 * x1 + coefficients.b2 * x2 -
            coefficients.a1 * y1 - coefficients.a2 * y2
        val output = raw.takeIf { it.isFinite() } ?: 0.0
        buffer[index] = output.coerceIn(-8.0, 8.0).toFloat()
        x2 = x1
        x1 = input
        y2 = y1
        y1 = output.coerceIn(-8.0, 8.0)
    }
    state.x1 = x1
    state.x2 = x2
    state.y1 = y1
    state.y2 = y2
}
