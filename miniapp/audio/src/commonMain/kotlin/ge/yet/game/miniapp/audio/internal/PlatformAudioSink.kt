package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.CompiledAudioProgram
import ge.yet.game.miniapp.audio.SfxName

internal interface PlatformAudioSink {
    fun openSession(id: MiniAppId, sessionKey: Long): PlatformAudioSinkSession
}

internal interface PlatformAudioSinkSession {
    val sampleRate: Int get() = DEFAULT_AUDIO_SAMPLE_RATE

    fun updatePolicy(policy: AudioSessionPolicy)
    fun playMusic(program: CompiledAudioProgram): AudioRuntimeSubmitResult
    fun stopMusic(fadeFrames: Int): AudioRuntimeSubmitResult
    fun playSfx(program: CompiledAudioProgram, name: SfxName): AudioRuntimeSubmitResult
    fun setControl(name: AudioControlName, value: Float): AudioRuntimeSubmitResult
    fun release()
    fun drainDiagnostics(): AudioRuntimeDiagnosticsSnapshot
}

internal data class AudioSessionPolicy(
    val musicGain: Float,
    val acceptsNewSfx: Boolean,
    val schedulingPaused: Boolean,
) {
    companion object {
        val Active = AudioSessionPolicy(musicGain = 1f, acceptsNewSfx = true, schedulingPaused = false)
        val Obscured = AudioSessionPolicy(musicGain = OBSCURED_MUSIC_GAIN, acceptsNewSfx = false, schedulingPaused = false)
        val Background = AudioSessionPolicy(musicGain = 0f, acceptsNewSfx = false, schedulingPaused = true)
    }
}

internal object UnavailablePlatformAudioSink : PlatformAudioSink {
    override fun openSession(id: MiniAppId, sessionKey: Long): PlatformAudioSinkSession =
        error("No platform procedural-audio sink is installed")
}

private const val DEFAULT_AUDIO_SAMPLE_RATE = 48_000
private const val OBSCURED_MUSIC_GAIN = 0.2f
