package ge.yet.game.miniapp.audio.internal.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal fun mixMonoToStereo(
    mono: FloatArray,
    left: FloatArray,
    right: FloatArray,
    frameCount: Int,
    gain: Float,
    pan: Float,
    monoOffset: Int = 0,
    outputOffset: Int = 0,
) {
    require(frameCount >= 0 && monoOffset >= 0 && monoOffset + frameCount <= mono.size)
    require(outputOffset >= 0 && outputOffset + frameCount <= left.size && outputOffset + frameCount <= right.size)
    val safeGain = gain.takeIf { it.isFinite() }?.coerceIn(0f, 4f) ?: 0f
    val angle = ((pan.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f) + 1f) * PI / 4.0
    val leftGain = (cos(angle) * safeGain).toFloat()
    val rightGain = (sin(angle) * safeGain).toFloat()
    for (frame in 0 until frameCount) {
        val input = mono[monoOffset + frame].takeIf { it.isFinite() } ?: 0f
        val index = outputOffset + frame
        left[index] += input * leftGain
        right[index] += input * rightGain
    }
}

internal class SmoothedGainState(initial: Float) {
    internal var current = initial.takeIf { it.isFinite() } ?: 0f
    internal var target = current
    internal var step = 0f
    internal var remaining = 0
}

internal fun applySmoothedGain(
    buffer: FloatArray,
    target: Float,
    rampFrames: Int,
    state: SmoothedGainState,
    frameCount: Int = buffer.size,
    offset: Int = 0,
) {
    require(rampFrames >= 0 && frameCount >= 0 && offset >= 0 && offset + frameCount <= buffer.size)
    val safeTarget = target.takeIf { it.isFinite() }?.coerceIn(0f, 4f) ?: 0f
    if (safeTarget != state.target) {
        state.target = safeTarget
        state.remaining = rampFrames
        state.step = if (rampFrames == 0) 0f else (safeTarget - state.current) / rampFrames
        if (rampFrames == 0) state.current = safeTarget
    }
    for (frame in 0 until frameCount) {
        if (state.remaining > 0) {
            state.current += state.step
            state.remaining -= 1
            if (state.remaining == 0) state.current = state.target
        }
        val index = offset + frame
        val input = buffer[index].takeIf { it.isFinite() } ?: 0f
        buffer[index] = input * state.current
    }
}
