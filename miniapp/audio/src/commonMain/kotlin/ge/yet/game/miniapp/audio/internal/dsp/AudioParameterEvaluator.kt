package ge.yet.game.miniapp.audio.internal.dsp

import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioParameter
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

internal fun evaluateAudioParameter(
    parameter: AudioParameter,
    absoluteFrame: Long,
    sampleRate: Int,
    controlPositions: Map<AudioControlName, Float>,
): Float {
    require(absoluteFrame >= 0 && sampleRate > 0)
    return when (parameter) {
        is AudioParameter.Constant -> parameter.value
        is AudioParameter.Control -> parameter.outputRange.interpolate(
            controlPositions[parameter.name]?.coerceIn(0f, 1f) ?: 0.5f,
        )
        is AudioParameter.SineLfo -> {
            val phase = absoluteFrame.toDouble() * parameter.rate.value / sampleRate + parameter.phaseCycles
            parameter.outputRange.interpolate(((sin(2.0 * PI * phase) + 1.0) * 0.5).toFloat())
        }
        is AudioParameter.SmoothNoise -> {
            val position = absoluteFrame.toDouble() * parameter.rate.value / sampleRate
            val segment = floor(position).toLong()
            val fraction = position - segment
            val smooth = fraction * fraction * (3.0 - 2.0 * fraction)
            val start = deterministicNoise(parameter.seed, segment)
            val end = deterministicNoise(parameter.seed, segment + 1)
            val normalized = ((start + (end - start) * smooth) + 1.0) * 0.5
            parameter.outputRange.interpolate(normalized.toFloat())
        }
        is AudioParameter.Product ->
            evaluateAudioParameter(parameter.left, absoluteFrame, sampleRate, controlPositions) *
                evaluateAudioParameter(parameter.right, absoluteFrame, sampleRate, controlPositions)
    }.takeIf(Float::isFinite) ?: 0f
}

private fun ClosedFloatingPointRange<Float>.interpolate(position: Float): Float =
    start + (endInclusive - start) * position.coerceIn(0f, 1f)

private fun deterministicNoise(seed: Long, segment: Long): Double {
    var value = seed xor (segment * -7046029254386353131L)
    value = (value xor (value ushr 30)) * -4658895280553007687L
    value = (value xor (value ushr 27)) * -7723592293110705685L
    value = value xor (value ushr 31)
    val unit = (value ushr 40).toDouble() / 16_777_215.0
    return unit * 2.0 - 1.0
}
