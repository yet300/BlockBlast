package ge.yet.game.miniapp.audio

/** Immutable, tempo-independent declarations that can be included in an audio program. */
class AudioProgramFragment internal constructor(
    internal val program: AudioProgram,
)

fun audioProgramFragment(block: AudioProgramFragmentBuilder.() -> Unit): AudioProgramFragment {
    val builder = AudioProgramFragmentBuilder().apply(block)
    return AudioProgramFragment(builder.build())
}

class AudioProgramFragmentBuilder internal constructor() {
    private val delegate = AudioProgramBuilder()

    fun control(
        name: String,
        default: Float,
        range: ClosedFloatingPointRange<Float>,
    ): AudioControlReference = delegate.control(name, default, range)

    fun control(name: String): AudioControlReference = delegate.control(name)

    fun instrument(name: String, block: InstrumentBuilder.() -> Unit) = delegate.instrument(name, block)

    fun musicTrack(name: String, block: MusicTrackBuilder.() -> Unit) = delegate.musicTrack(name, block)

    fun sfx(name: String, block: SoundEffectBuilder.() -> Unit) = delegate.sfx(name, block)

    fun musicBus(block: SendEffectBuilder.() -> Unit) = delegate.musicBus(block)

    fun sfxBus(block: SendEffectBuilder.() -> Unit) = delegate.sfxBus(block)

    fun include(fragment: AudioProgramFragment) = delegate.include(fragment)

    internal fun build(): AudioProgram = delegate.build()
}
