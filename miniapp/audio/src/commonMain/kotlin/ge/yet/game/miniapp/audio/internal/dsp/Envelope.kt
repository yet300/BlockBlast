package ge.yet.game.miniapp.audio.internal.dsp

import kotlin.math.roundToInt

internal enum class EnvelopePhase { IDLE, ATTACK, DECAY, SUSTAIN, RELEASE, DONE }

internal class EnvelopeState(
    sampleRate: Int,
    attackSeconds: Double,
    decaySeconds: Double,
    private val sustain: Float,
    releaseSeconds: Double,
) {
    private val attackFrames = durationFrames(sampleRate, attackSeconds)
    private val decayFrames = durationFrames(sampleRate, decaySeconds)
    private val releaseFrames = durationFrames(sampleRate, releaseSeconds)
    private var phaseFrame = 0
    private var current = 0f
    private var releaseStart = 0f
    var phase: EnvelopePhase = EnvelopePhase.IDLE
        private set

    init {
        require(sampleRate > 0)
        require(sustain.isFinite() && sustain in 0f..1f)
    }

    fun noteOn() {
        phase = if (attackFrames == 0) {
            current = 1f
            if (decayFrames == 0) {
                current = sustain
                EnvelopePhase.SUSTAIN
            } else {
                EnvelopePhase.DECAY
            }
        } else {
            current = 0f
            EnvelopePhase.ATTACK
        }
        phaseFrame = 0
    }

    fun noteOff() {
        if (phase == EnvelopePhase.IDLE || phase == EnvelopePhase.DONE) return
        releaseStart = current
        phaseFrame = 0
        phase = if (releaseFrames == 0) {
            current = 0f
            EnvelopePhase.DONE
        } else {
            EnvelopePhase.RELEASE
        }
    }

    fun nextValue(): Float {
        when (phase) {
            EnvelopePhase.IDLE, EnvelopePhase.DONE -> current = 0f
            EnvelopePhase.ATTACK -> {
                phaseFrame += 1
                current = phaseFrame.toFloat() / attackFrames
                if (phaseFrame >= attackFrames) transitionFromAttack()
            }
            EnvelopePhase.DECAY -> {
                phaseFrame += 1
                current = 1f - (1f - sustain) * phaseFrame / decayFrames
                if (phaseFrame >= decayFrames) {
                    current = sustain
                    phase = EnvelopePhase.SUSTAIN
                    phaseFrame = 0
                }
            }
            EnvelopePhase.SUSTAIN -> current = sustain
            EnvelopePhase.RELEASE -> {
                phaseFrame += 1
                current = releaseStart * (1f - phaseFrame.toFloat() / releaseFrames)
                if (phaseFrame >= releaseFrames) {
                    current = 0f
                    phase = EnvelopePhase.DONE
                    phaseFrame = 0
                }
            }
        }
        return current.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    }

    private fun transitionFromAttack() {
        current = 1f
        phaseFrame = 0
        phase = if (decayFrames == 0) {
            current = sustain
            EnvelopePhase.SUSTAIN
        } else {
            EnvelopePhase.DECAY
        }
    }
}

internal fun applyEnvelope(
    buffer: FloatArray,
    state: EnvelopeState,
    frameCount: Int,
    offset: Int = 0,
) {
    require(frameCount >= 0 && offset >= 0 && offset + frameCount <= buffer.size)
    for (frame in 0 until frameCount) {
        val index = offset + frame
        val input = buffer[index].takeIf { it.isFinite() } ?: 0f
        buffer[index] = input * state.nextValue()
    }
}

private fun durationFrames(sampleRate: Int, seconds: Double): Int {
    require(seconds.isFinite() && seconds >= 0.0)
    return (seconds * sampleRate).roundToInt().coerceAtLeast(0)
}
