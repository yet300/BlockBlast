package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.audio.AudioCompilationResult
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioDiagnostic
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.MusicTrackDeclaration
import ge.yet.game.miniapp.audio.SendEffectDeclaration
import ge.yet.game.miniapp.audio.compile
import ge.yet.game.miniapp.audio.internal.dsp.DelayState
import ge.yet.game.miniapp.audio.internal.dsp.ReverbState
import ge.yet.game.miniapp.audio.internal.dsp.VoiceState
import ge.yet.game.miniapp.audio.internal.dsp.applyDelay
import ge.yet.game.miniapp.audio.internal.dsp.applyReverb
import ge.yet.game.miniapp.audio.internal.dsp.limitStereo
import ge.yet.game.miniapp.audio.internal.dsp.mixMonoToStereo
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class OfflineAudioRequest(
    val sampleRate: Int,
    val frameCount: Int,
) {
    init {
        require(sampleRate in 8_000..192_000)
        require(frameCount > 0 && frameCount <= sampleRate * 60)
    }
}

internal sealed interface OfflineAudioRenderResult {
    class Success(val audio: OfflineAudioResult) : OfflineAudioRenderResult
    class Failure(diagnostics: List<AudioDiagnostic>) : OfflineAudioRenderResult {
        val diagnostics = diagnostics.toList()
    }
}

internal class OfflineAudioResult internal constructor(
    left: FloatArray,
    right: FloatArray,
) {
    val left: FloatArray = left.copyOf()
    val right: FloatArray = right.copyOf()
    val frameCount: Int get() = left.size
    val peak: Float = calculatePeak(this.left, this.right)
    val rms: Double = calculateRms(this.left, this.right)

    fun quantizedPcmHash(): Long {
        var hash = -3750763034362895579L
        for (index in left.indices) {
            hash = hashQuantizedSample(hash, left[index])
            hash = hashQuantizedSample(hash, right[index])
        }
        return hash
    }
}

internal object OfflineAudioRenderer {
    fun render(program: AudioProgram, request: OfflineAudioRequest): OfflineAudioRenderResult {
        val compiled = when (val compilation = program.compile()) {
            is AudioCompilationResult.Failure -> return OfflineAudioRenderResult.Failure(compilation.diagnostics)
            is AudioCompilationResult.Success -> compilation.program
        }

        val left = FloatArray(request.frameCount)
        val right = FloatArray(request.frameCount)
        val controlPositions = program.controls.associate { control ->
            val width = control.range.endInclusive - control.range.start
            control.name to if (width == 0f) 0f else (control.default - control.range.start) / width
        }
        val scheduled = AudioScheduler(compiled, request.sampleRate).scheduleBlock(0, request.frameCount)
        program.musicTracks.forEachIndexed { trackIndex, track ->
            renderTrack(
                program = program,
                track = track,
                events = scheduled.filter { it.trackIndex == trackIndex },
                request = request,
                controlPositions = controlPositions,
                left = left,
                right = right,
            )
        }
        applyBusEffects(left, program.musicBus.effects, request.sampleRate)
        applyBusEffects(right, program.musicBus.effects, request.sampleRate)
        limitStereo(left, right, request.frameCount)
        return OfflineAudioRenderResult.Success(OfflineAudioResult(left, right))
    }
}

private fun renderTrack(
    program: AudioProgram,
    track: MusicTrackDeclaration,
    events: List<ScheduledAudioEvent>,
    request: OfflineAudioRequest,
    controlPositions: Map<AudioControlName, Float>,
    left: FloatArray,
    right: FloatArray,
) {
    val instrument = program.instruments.first { it.name == track.instrument }
    val mono = FloatArray(request.frameCount)
    events.forEach { event ->
        val startFrame = event.frameOffset
        val frames = minOf(event.durationFrames, (request.frameCount - startFrame).toLong()).toInt()
        if (frames == 0) return@forEach
        val voiceBuffer = FloatArray(frames)
        VoiceState(instrument, event.note, request.sampleRate, frames, controlPositions).render(voiceBuffer, frames)
        for (frame in 0 until frames) mono[startFrame + frame] += voiceBuffer[frame]
    }
    applySendEffects(mono, track.effects, request.sampleRate)
    mixMonoToStereo(mono, left, right, request.frameCount, gain = 1f, pan = 0f)
}

private fun applyBusEffects(buffer: FloatArray, effects: List<SendEffectDeclaration>, sampleRate: Int) =
    applySendEffects(buffer, effects, sampleRate)

private fun applySendEffects(buffer: FloatArray, effects: List<SendEffectDeclaration>, sampleRate: Int) {
    effects.forEach { effect ->
        when (effect) {
            is SendEffectDeclaration.Delay -> applyDelay(
                buffer = buffer,
                delayFrames = (effect.time.seconds * sampleRate).roundToInt().coerceAtLeast(1),
                feedback = effect.feedback,
                wet = 1f,
                state = DelayState((4.0 * sampleRate).roundToInt()),
            )
            is SendEffectDeclaration.Reverb -> applyReverb(buffer, effect.send, ReverbState(sampleRate))
        }
    }
}

private fun calculatePeak(left: FloatArray, right: FloatArray): Float {
    var peak = 0f
    for (index in left.indices) peak = maxOf(peak, abs(left[index]), abs(right[index]))
    return peak
}

private fun calculateRms(left: FloatArray, right: FloatArray): Double {
    var sum = 0.0
    for (index in left.indices) sum += left[index] * left[index] + right[index] * right[index]
    return sqrt(sum / (left.size * 2.0))
}

private fun hashQuantizedSample(initial: Long, sample: Float): Long {
    val quantized = (sample.coerceIn(-1f, 1f) * 32_767f).roundToInt()
    var hash = (initial xor (quantized and 0xFF).toLong()) * 1_099_511_628_211L
    hash = (hash xor ((quantized ushr 8) and 0xFF).toLong()) * 1_099_511_628_211L
    return hash
}
