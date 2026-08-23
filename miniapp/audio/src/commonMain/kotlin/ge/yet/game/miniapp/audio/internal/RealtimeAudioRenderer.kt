package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioMobileBudget
import ge.yet.game.miniapp.audio.CompiledAudioProgram
import ge.yet.game.miniapp.audio.InstrumentDeclaration
import ge.yet.game.miniapp.audio.InstrumentName
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.miniapp.audio.MusicTrackDeclaration
import ge.yet.game.miniapp.audio.SoundEffectDeclaration
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.audio.internal.dsp.SmoothedGainState
import ge.yet.game.miniapp.audio.internal.dsp.VoiceState
import ge.yet.game.miniapp.audio.internal.dsp.applySmoothedGain
import ge.yet.game.miniapp.audio.internal.dsp.limitStereo
import ge.yet.game.miniapp.audio.internal.dsp.mixMonoToStereo
import ge.yet.game.miniapp.audio.internal.dsp.mixMonoToStereoAutomated
import kotlin.math.ln
import kotlin.math.roundToInt

/** Shared PCM block renderer. Platform sinks own its thread confinement. */
internal class RealtimeAudioRenderer(
    private val sampleRate: Int,
    private val blockCapacity: Int,
) : AudioRuntimeCommandTarget {
    private val musicVoices = mutableListOf<MusicVoice>()
    private val sfxVoices = mutableListOf<SfxVoice>()
    private val controlPositions = mutableMapOf<AudioControlName, Float>()
    private val leftPolicyGain = SmoothedGainState(1f)
    private val rightPolicyGain = SmoothedGainState(1f)
    private val leftStopGain = SmoothedGainState(1f)
    private val rightStopGain = SmoothedGainState(1f)
    private val musicLeft = FloatArray(blockCapacity)
    private val musicRight = FloatArray(blockCapacity)
    private var program: CompiledAudioProgram? = null
    private var scheduler: AudioScheduler? = null
    private var framePosition = 0L
    private var policy = AudioSessionPolicy.Active
    private var stopAfterFade = false
    private var stopFadeFrames = 0

    val hasActiveAudio: Boolean get() = program != null || sfxVoices.isNotEmpty()

    init {
        require(sampleRate in 8_000..192_000)
        require(blockCapacity > 0)
    }

    fun updatePolicy(value: AudioSessionPolicy) {
        policy = value
    }

    fun render(left: FloatArray, right: FloatArray, frameCount: Int) {
        require(frameCount in 0..blockCapacity)
        require(left.size >= frameCount && right.size >= frameCount)
        left.fill(0f, 0, frameCount)
        right.fill(0f, 0, frameCount)
        musicLeft.fill(0f, 0, frameCount)
        musicRight.fill(0f, 0, frameCount)
        if (policy.schedulingPaused || frameCount == 0) return

        val activeProgram = program
        if (activeProgram != null) {
            scheduleNewMusicVoices(activeProgram, frameCount)
            renderMusicVoices(musicLeft, musicRight, frameCount)
            framePosition += frameCount
        }
        val stopTarget = if (stopAfterFade) 0f else 1f
        applySmoothedGain(musicLeft, stopTarget, stopFadeFrames, leftStopGain, frameCount)
        applySmoothedGain(musicRight, stopTarget, stopFadeFrames, rightStopGain, frameCount)
        applySmoothedGain(musicLeft, policy.musicGain, POLICY_RAMP_FRAMES, leftPolicyGain, frameCount)
        applySmoothedGain(musicRight, policy.musicGain, POLICY_RAMP_FRAMES, rightPolicyGain, frameCount)
        if (stopAfterFade && leftStopGain.remaining == 0 && leftStopGain.current == 0f) clearMusic()
        for (frame in 0 until frameCount) {
            left[frame] = musicLeft[frame]
            right[frame] = musicRight[frame]
        }
        renderSfxVoices(left, right, frameCount)
        limitStereo(left, right, frameCount)
    }

    override fun playMusic(program: CompiledAudioProgram): AudioRuntimeCommandOutcome {
        clearMusic()
        this.program = program
        scheduler = AudioScheduler(program, sampleRate)
        program.source.controls.forEach { declaration ->
            val width = declaration.range.endInclusive - declaration.range.start
            controlPositions[declaration.name] = if (width == 0f) {
                0f
            } else {
                (declaration.default - declaration.range.start) / width
            }
        }
        resetGain(leftPolicyGain, policy.musicGain)
        resetGain(rightPolicyGain, policy.musicGain)
        resetGain(leftStopGain, 1f)
        resetGain(rightStopGain, 1f)
        return AudioRuntimeCommandOutcome.APPLIED
    }

    override fun stopMusic(fadeFrames: Int): AudioRuntimeCommandOutcome {
        if (fadeFrames == 0) {
            clearMusic()
        } else {
            stopAfterFade = true
            stopFadeFrames = fadeFrames
        }
        return AudioRuntimeCommandOutcome.APPLIED
    }

    override fun playSfx(program: CompiledAudioProgram, name: SfxName): AudioRuntimeCommandOutcome {
        val declaration = program.source.soundEffects.firstOrNull { it.name == name }
            ?: return AudioRuntimeCommandOutcome.VALIDATION_REJECTED
        val shed = sfxVoices.size + musicVoices.size >= AudioMobileBudget.MAX_VOICES
        if (shed) {
            if (musicVoices.isNotEmpty()) musicVoices.removeAt(0) else sfxVoices.removeAt(0)
        }
        sfxVoices += declaration.toVoice()
        return if (shed) AudioRuntimeCommandOutcome.FORCED_VOICE_SHEDDING else AudioRuntimeCommandOutcome.APPLIED
    }

    override fun setControl(name: AudioControlName, value: Float): AudioRuntimeCommandOutcome {
        val declaration = program?.source?.controls?.firstOrNull { it.name == name }
            ?: return AudioRuntimeCommandOutcome.VALIDATION_REJECTED
        val width = declaration.range.endInclusive - declaration.range.start
        controlPositions[name] = if (width == 0f) 0f else (value - declaration.range.start) / width
        return AudioRuntimeCommandOutcome.APPLIED
    }

    override fun destroy(): AudioRuntimeCommandOutcome {
        clearMusic()
        sfxVoices.clear()
        return AudioRuntimeCommandOutcome.APPLIED
    }

    private fun scheduleNewMusicVoices(program: CompiledAudioProgram, frameCount: Int) {
        val scheduled = requireNotNull(scheduler).scheduleBlockInto(framePosition, frameCount)
        for (index in 0 until scheduled.size) {
            val track = program.source.musicTracks[scheduled.trackIndexAt(index)]
            val instrument = program.source.instruments.first { it.name == track.instrument }
            if (musicVoices.size == AudioMobileBudget.MAX_VOICES) musicVoices.removeAt(0)
            musicVoices += MusicVoice(
                state = VoiceState(instrument, scheduled.noteAt(index), sampleRate, blockCapacity, controlPositions),
                track = track,
                remainingFrames = scheduled.durationFramesAt(index),
                blockOffset = scheduled.frameOffsetAt(index),
                scratch = FloatArray(blockCapacity),
            )
        }
    }

    private fun renderMusicVoices(left: FloatArray, right: FloatArray, frameCount: Int) {
        val iterator = musicVoices.iterator()
        while (iterator.hasNext()) {
            val voice = iterator.next()
            val availableFrames = frameCount - voice.blockOffset
            val renderedFrames = minOf(voice.remainingFrames, availableFrames.toLong()).toInt()
            if (renderedFrames > 0) {
                voice.scratch.fill(0f, 0, frameCount)
                voice.state.render(voice.scratch, renderedFrames, voice.blockOffset)
                mixMonoToStereoAutomated(
                    mono = voice.scratch,
                    left = left,
                    right = right,
                    frameCount = frameCount,
                    sampleRate = sampleRate,
                    gain = voice.track.gain,
                    pan = voice.track.pan,
                    controlPositions = controlPositions,
                    absoluteStartFrame = framePosition,
                )
                voice.remainingFrames -= renderedFrames
            }
            voice.blockOffset = 0
            if (voice.remainingFrames <= 0) iterator.remove()
        }
    }

    private fun renderSfxVoices(left: FloatArray, right: FloatArray, frameCount: Int) {
        val iterator = sfxVoices.iterator()
        while (iterator.hasNext()) {
            val voice = iterator.next()
            if (!voice.releasing && voice.renderedFrames >= voice.releaseStartFrame) {
                voice.state.noteOff()
                voice.releasing = true
            }
            voice.scratch.fill(0f, 0, frameCount)
            voice.state.render(voice.scratch, frameCount)
            mixMonoToStereo(voice.scratch, left, right, frameCount, gain = 1f, pan = 0f)
            voice.renderedFrames += frameCount
            if (voice.state.isFinished) iterator.remove()
        }
    }

    private fun SoundEffectDeclaration.toVoice(): SfxVoice {
        val instrument = InstrumentDeclaration(
            name = InstrumentName("runtime_sfx"),
            oscillators = oscillators,
            noises = noises,
            partials = partials,
            envelope = envelope,
            frequencyModulation = frequencyModulation,
            vibrato = vibrato,
            filters = filters,
            effects = effects,
        )
        val frequency = pitch?.from?.value ?: DEFAULT_SFX_FREQUENCY
        val midi = (MIDI_A4 + MIDI_NOTES_PER_OCTAVE * ln(frequency / DEFAULT_SFX_FREQUENCY) / ln(2.0))
            .roundToInt()
            .coerceIn(MIDI_MIN, MIDI_MAX)
        val releaseStartFrame = ((pitch?.duration?.seconds ?: DEFAULT_SFX_HOLD_SECONDS) * sampleRate)
            .roundToInt()
            .coerceAtLeast(1)
        return SfxVoice(
            state = VoiceState(
                instrument = instrument,
                note = MidiNote.of(midi),
                sampleRate = sampleRate,
                blockCapacity = blockCapacity,
                controlPositions = controlPositions,
                pitch = pitch,
            ),
            releaseStartFrame = releaseStartFrame,
            scratch = FloatArray(blockCapacity),
        )
    }

    private fun clearMusic() {
        program = null
        scheduler = null
        musicVoices.clear()
        controlPositions.clear()
        framePosition = 0L
        stopAfterFade = false
        stopFadeFrames = 0
    }

    private class MusicVoice(
        val state: VoiceState,
        val track: MusicTrackDeclaration,
        var remainingFrames: Long,
        var blockOffset: Int,
        val scratch: FloatArray,
    )

    private class SfxVoice(
        val state: VoiceState,
        val releaseStartFrame: Int,
        val scratch: FloatArray,
        var renderedFrames: Int = 0,
        var releasing: Boolean = false,
    )
}

private fun resetGain(state: SmoothedGainState, value: Float) {
    state.current = value
    state.target = value
    state.step = 0f
    state.remaining = 0
}

private const val POLICY_RAMP_FRAMES = 128
private const val DEFAULT_SFX_FREQUENCY = 440.0
private const val DEFAULT_SFX_HOLD_SECONDS = 0.05
private const val MIDI_A4 = 69.0
private const val MIDI_NOTES_PER_OCTAVE = 12.0
private const val MIDI_MIN = 0
private const val MIDI_MAX = 127
