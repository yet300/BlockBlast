package ge.yet.game.miniapp.audio

enum class AudioDiagnosticCode {
    EMPTY_OSCILLATOR_SOURCE,
    UNRESOLVED_INSTRUMENT,
    TRACK_LIMIT_EXCEEDED,
    OSCILLATOR_LIMIT_EXCEEDED,
}

data class AudioDiagnostic(
    val code: AudioDiagnosticCode,
    val path: String,
    val message: String,
)

internal class CompiledAudioProgram internal constructor(
    internal val source: AudioProgram,
) {
    val tempo: Tempo get() = source.tempo
    val trackCount: Int get() = source.musicTracks.size
}

internal sealed interface AudioCompilationResult {
    class Success internal constructor(val program: CompiledAudioProgram) : AudioCompilationResult

    class Failure(diagnostics: List<AudioDiagnostic>) : AudioCompilationResult {
        val diagnostics: List<AudioDiagnostic> = diagnostics.toList()
    }
}

internal fun AudioProgram.compile(): AudioCompilationResult {
    val diagnostics = buildList {
        instruments.forEach { instrument ->
            if (instrument.oscillators.isEmpty()) {
                add(
                    AudioDiagnostic(
                        code = AudioDiagnosticCode.EMPTY_OSCILLATOR_SOURCE,
                        path = "instrument[${instrument.name.value}].oscillators",
                        message = "Instrument '${instrument.name.value}' requires at least one oscillator",
                    ),
                )
            }
            if (instrument.oscillators.size > AudioMobileBudget.MAX_OSCILLATORS_PER_INSTRUMENT) {
                add(
                    AudioDiagnostic(
                        code = AudioDiagnosticCode.OSCILLATOR_LIMIT_EXCEEDED,
                        path = "instrument[${instrument.name.value}].oscillators",
                        message = "Instrument '${instrument.name.value}' exceeds the mobile oscillator limit",
                    ),
                )
            }
        }

        val instrumentNames = instruments.mapTo(mutableSetOf()) { it.name }
        musicTracks.forEach { track ->
            if (track.instrument !in instrumentNames) {
                add(
                    AudioDiagnostic(
                        code = AudioDiagnosticCode.UNRESOLVED_INSTRUMENT,
                        path = "musicTrack[${track.name.value}].instrument[${track.instrument.value}]",
                        message = "Unknown instrument '${track.instrument.value}'",
                    ),
                )
            }
        }

        soundEffects.forEach { effect ->
            if (effect.oscillators.isEmpty()) {
                add(
                    AudioDiagnostic(
                        code = AudioDiagnosticCode.EMPTY_OSCILLATOR_SOURCE,
                        path = "sfx[${effect.name.value}].oscillators",
                        message = "SFX '${effect.name.value}' requires at least one oscillator",
                    ),
                )
            }
        }

        if (musicTracks.size > AudioMobileBudget.MAX_TRACKS) {
            add(
                AudioDiagnostic(
                    code = AudioDiagnosticCode.TRACK_LIMIT_EXCEEDED,
                    path = "musicTracks",
                    message = "Program exceeds the mobile track limit",
                ),
            )
        }
    }.sortedWith(compareBy(AudioDiagnostic::path, AudioDiagnostic::code))

    return if (diagnostics.isEmpty()) {
        AudioCompilationResult.Success(CompiledAudioProgram(this))
    } else {
        AudioCompilationResult.Failure(diagnostics)
    }
}

internal object AudioMobileBudget {
    const val MAX_TRACKS = 16
    const val MAX_OSCILLATORS_PER_INSTRUMENT = 8
}
