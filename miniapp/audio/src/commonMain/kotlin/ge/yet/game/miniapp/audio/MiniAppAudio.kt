package ge.yet.game.miniapp.audio

interface MiniAppAudio {
    fun playMusic(program: AudioProgram): AudioCommandResult
    fun stopMusic(fadeOut: AudioDuration = AudioDuration.DefaultFade): AudioCommandResult
    fun playSfx(program: AudioProgram, name: SfxName): AudioCommandResult
    fun setControl(name: AudioControlName, value: Float): AudioCommandResult
}

enum class AudioCommandRejection {
    INVALID_PROGRAM,
    UNKNOWN_SFX,
    UNKNOWN_CONTROL,
    CONTROL_OUT_OF_RANGE,
    SESSION_CLOSED,
    COMMAND_QUEUE_FULL,
    BACKEND_UNAVAILABLE,
}

sealed interface AudioCommandResult {
    data object Accepted : AudioCommandResult

    class Rejected(
        val reason: AudioCommandRejection,
        diagnostics: List<AudioDiagnostic> = emptyList(),
    ) : AudioCommandResult {
        val diagnostics: List<AudioDiagnostic> = diagnostics.toList()
    }
}
