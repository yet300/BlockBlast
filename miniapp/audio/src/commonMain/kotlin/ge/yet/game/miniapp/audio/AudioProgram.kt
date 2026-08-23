package ge.yet.game.miniapp.audio

class AudioProgram internal constructor(
    val tempo: Tempo,
    controls: List<AudioControlDeclaration>,
    instruments: List<InstrumentDeclaration>,
    musicTracks: List<MusicTrackDeclaration>,
    soundEffects: List<SoundEffectDeclaration>,
) {
    val controls: List<AudioControlDeclaration> = controls.toList()
    val instruments: List<InstrumentDeclaration> = instruments.map { it.copy(oscillators = it.oscillators.toList()) }
    val musicTracks: List<MusicTrackDeclaration> = musicTracks.map { it.copy(notes = it.notes.toList()) }
    val soundEffects: List<SoundEffectDeclaration> = soundEffects.map { it.copy(oscillators = it.oscillators.toList()) }
}
