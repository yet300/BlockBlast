package ge.yet.game.miniapp.audio

class AudioProgram internal constructor(
    val tempo: Tempo,
    controls: List<AudioControlDeclaration>,
    instruments: List<InstrumentDeclaration>,
    musicTracks: List<MusicTrackDeclaration>,
    soundEffects: List<SoundEffectDeclaration>,
    musicBus: AudioBusDeclaration,
    sfxBus: AudioBusDeclaration,
) {
    val controls: List<AudioControlDeclaration> = controls.toList()
    val instruments: List<InstrumentDeclaration> = instruments.map { it.snapshot() }
    val musicTracks: List<MusicTrackDeclaration> = musicTracks.map { it.copy(notes = it.notes.toList(), effects = it.effects.toList()) }
    val soundEffects: List<SoundEffectDeclaration> = soundEffects.map { it.snapshot() }
    val musicBus: AudioBusDeclaration = musicBus.copy(effects = musicBus.effects.toList())
    val sfxBus: AudioBusDeclaration = sfxBus.copy(effects = sfxBus.effects.toList())
}

private fun InstrumentDeclaration.snapshot() = copy(
    oscillators = oscillators.toList(),
    noises = noises.toList(),
    partials = partials.toList(),
    filters = filters.toList(),
    effects = effects.toList(),
)

private fun SoundEffectDeclaration.snapshot() = copy(
    oscillators = oscillators.toList(),
    noises = noises.toList(),
    partials = partials.toList(),
    filters = filters.toList(),
    effects = effects.toList(),
)

sealed interface AudioLookupResult<out T> {
    data class Found<T>(val value: T) : AudioLookupResult<T>
    data class Missing(val path: String) : AudioLookupResult<Nothing>
}

fun AudioProgram.sfx(name: SfxName): AudioLookupResult<SoundEffectDeclaration> =
    soundEffects.firstOrNull { it.name == name }
        ?.let { AudioLookupResult.Found(it) }
        ?: AudioLookupResult.Missing("sfx[${name.value}]")

fun AudioProgram.control(name: AudioControlName): AudioLookupResult<AudioControlDeclaration> =
    controls.firstOrNull { it.name == name }
        ?.let { AudioLookupResult.Found(it) }
        ?: AudioLookupResult.Missing("control[${name.value}]")
