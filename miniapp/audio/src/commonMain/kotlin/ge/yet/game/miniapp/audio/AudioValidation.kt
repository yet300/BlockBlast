package ge.yet.game.miniapp.audio

import ge.yet.game.pattern.PatternQueryBudget
import ge.yet.game.pattern.PatternQueryException
import ge.yet.game.pattern.PatternQueryLimit
import ge.yet.game.pattern.TimeArc

enum class AudioDiagnosticCode {
    EMPTY_OSCILLATOR_SOURCE,
    UNRESOLVED_INSTRUMENT,
    TRACK_LIMIT_EXCEEDED,
    OSCILLATOR_LIMIT_EXCEEDED,
    EFFECT_LIMIT_EXCEEDED,
    DELAY_LIMIT_EXCEEDED,
    FEEDBACK_LIMIT_EXCEEDED,
    NOISE_LIMIT_EXCEEDED,
    PARTIAL_LIMIT_EXCEEDED,
    FILTER_LIMIT_EXCEEDED,
    VOICE_EFFECT_LIMIT_EXCEEDED,
    UNRESOLVED_CONTROL,
    PATTERN_EVENT_LIMIT_EXCEEDED,
    PATTERN_OPERATION_LIMIT_EXCEEDED,
    PARAMETER_RANGE_INVALID,
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
            if (!instrument.hasSource()) {
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
            validateVoiceCollections(
                ownerPath = "instrument[${instrument.name.value}]",
                noises = instrument.noises.size,
                partials = instrument.partials.size,
                filters = instrument.filters.size,
                effects = instrument.effects.size,
                diagnostics = this,
            )
        }

        val instrumentNames = instruments.mapTo(mutableSetOf()) { it.name }
        val controlNames = controls.mapTo(mutableSetOf()) { it.name }
        instruments.forEach { instrument ->
            validateControlReferences("instrument[${instrument.name.value}]", instrument.filters, controlNames, this)
        }
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
            validateEffects("musicTrack[${track.name.value}]", track.effects, this)
            validatePattern(track, this)
        }

        soundEffects.forEach { effect ->
            if (!effect.hasSource()) {
                add(
                    AudioDiagnostic(
                        code = AudioDiagnosticCode.EMPTY_OSCILLATOR_SOURCE,
                        path = "sfx[${effect.name.value}].oscillators",
                        message = "SFX '${effect.name.value}' requires at least one oscillator",
                    ),
                )
            }
            if (effect.oscillators.size > AudioMobileBudget.MAX_OSCILLATORS_PER_INSTRUMENT) {
                add(
                    AudioDiagnostic(
                        code = AudioDiagnosticCode.OSCILLATOR_LIMIT_EXCEEDED,
                        path = "sfx[${effect.name.value}].oscillators",
                        message = "SFX '${effect.name.value}' exceeds the mobile oscillator limit",
                    ),
                )
            }
            validateVoiceCollections(
                ownerPath = "sfx[${effect.name.value}]",
                noises = effect.noises.size,
                partials = effect.partials.size,
                filters = effect.filters.size,
                effects = effect.effects.size,
                diagnostics = this,
            )
            validateControlReferences("sfx[${effect.name.value}]", effect.filters, controlNames, this)
        }

        validateEffects("musicBus", musicBus.effects, this)
        validateEffects("sfxBus", sfxBus.effects, this)

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

private fun validateControlReferences(
    ownerPath: String,
    filters: List<FilterDeclaration>,
    controls: Set<AudioControlName>,
    diagnostics: MutableList<AudioDiagnostic>,
) {
    filters.forEachIndexed { index, filter ->
        val parameter = filter.frequency
        val field = if (filter is FilterDeclaration.BandPass) "centerHz" else "cutoffHz"
        if (parameter is AudioParameter.Control && parameter.name !in controls) {
            diagnostics += AudioDiagnostic(
                code = AudioDiagnosticCode.UNRESOLVED_CONTROL,
                path = "$ownerPath.filter[$index].$field",
                message = "Unknown control '${parameter.name.value}'",
            )
        }
        if (parameter.outputRange.start <= 0f) {
            diagnostics += AudioDiagnostic(
                code = AudioDiagnosticCode.PARAMETER_RANGE_INVALID,
                path = "$ownerPath.filter[$index].$field",
                message = "Filter frequency must remain positive",
            )
        }
    }
}

private fun validatePattern(
    track: MusicTrackDeclaration,
    diagnostics: MutableList<AudioDiagnostic>,
) {
    try {
        track.pattern.query(TimeArc.unit, PatternQueryBudget())
    } catch (failure: PatternQueryException) {
        diagnostics += AudioDiagnostic(
            code = when (failure.limit) {
                PatternQueryLimit.EVENTS -> AudioDiagnosticCode.PATTERN_EVENT_LIMIT_EXCEEDED
                PatternQueryLimit.OPERATIONS -> AudioDiagnosticCode.PATTERN_OPERATION_LIMIT_EXCEEDED
            },
            path = "musicTrack[${track.name.value}].pattern",
            message = failure.message ?: "Pattern query exceeded its mobile budget",
        )
    }
}

internal object AudioMobileBudget {
    const val MAX_TRACKS = 16
    const val MAX_OSCILLATORS_PER_INSTRUMENT = 8
    const val MAX_EFFECTS = 4
    const val MAX_DELAY_SECONDS = 4.0
    const val MAX_DELAY_FEEDBACK = 0.95f
    const val MAX_NOISE_SOURCES = 4
    const val MAX_ADDITIVE_PARTIALS = 32
    const val MAX_FILTERS = 4
    const val MAX_VOICE_EFFECTS = 4
}

private fun InstrumentDeclaration.hasSource(): Boolean =
    oscillators.isNotEmpty() || noises.isNotEmpty() || partials.isNotEmpty()

private fun SoundEffectDeclaration.hasSource(): Boolean =
    oscillators.isNotEmpty() || noises.isNotEmpty() || partials.isNotEmpty()

private fun validateEffects(
    ownerPath: String,
    effects: List<SendEffectDeclaration>,
    diagnostics: MutableList<AudioDiagnostic>,
) {
    effects.forEachIndexed { index, effect ->
        if (effect is SendEffectDeclaration.Delay) {
            if (effect.time.seconds > AudioMobileBudget.MAX_DELAY_SECONDS) {
                diagnostics += AudioDiagnostic(
                    code = AudioDiagnosticCode.DELAY_LIMIT_EXCEEDED,
                    path = "$ownerPath.effect[$index].delaySeconds",
                    message = "Delay exceeds the mobile four-second limit",
                )
            }
            if (effect.feedback > AudioMobileBudget.MAX_DELAY_FEEDBACK) {
                diagnostics += AudioDiagnostic(
                    code = AudioDiagnosticCode.FEEDBACK_LIMIT_EXCEEDED,
                    path = "$ownerPath.effect[$index].feedback",
                    message = "Delay feedback exceeds the mobile 0.95 limit",
                )
            }
        }
    }
    if (effects.size > AudioMobileBudget.MAX_EFFECTS) {
        diagnostics += AudioDiagnostic(
            code = AudioDiagnosticCode.EFFECT_LIMIT_EXCEEDED,
            path = "$ownerPath.effects",
            message = "Effect chain exceeds the mobile limit",
        )
    }
}

private fun validateVoiceCollections(
    ownerPath: String,
    noises: Int,
    partials: Int,
    filters: Int,
    effects: Int,
    diagnostics: MutableList<AudioDiagnostic>,
) {
    fun addIfExceeded(size: Int, limit: Int, code: AudioDiagnosticCode, segment: String) {
        if (size > limit) {
            diagnostics += AudioDiagnostic(
                code = code,
                path = "$ownerPath.$segment",
                message = "$segment exceeds the mobile limit of $limit",
            )
        }
    }

    addIfExceeded(noises, AudioMobileBudget.MAX_NOISE_SOURCES, AudioDiagnosticCode.NOISE_LIMIT_EXCEEDED, "noises")
    addIfExceeded(partials, AudioMobileBudget.MAX_ADDITIVE_PARTIALS, AudioDiagnosticCode.PARTIAL_LIMIT_EXCEEDED, "partials")
    addIfExceeded(filters, AudioMobileBudget.MAX_FILTERS, AudioDiagnosticCode.FILTER_LIMIT_EXCEEDED, "filters")
    addIfExceeded(effects, AudioMobileBudget.MAX_VOICE_EFFECTS, AudioDiagnosticCode.VOICE_EFFECT_LIMIT_EXCEEDED, "effects")
}
