# MiniApp procedural audio: getting started

MiniApp audio is Kotlin-only, asset-free and shared by Android and iOS. A game declares one immutable `AudioProgram`, receives the session-bound `MiniAppAudio` from `MiniAppSessionContext` or Metro, and sends commands from its component/state holder. Compose UI must not own playback.

Generated game modules applying `logica.miniapp` already receive the public audio API and shared presets; do not duplicate those dependencies. A non-MiniApp module must declare only the narrow dependency it actually authors against.

Start by composing presets:

```kotlin
private object MenuAudio {
    val Intensity = AudioControlName("intensity")
    val Placement = SfxName("placement")

    val program = audioProgram {
        tempo(84f)
        val intensity = control("intensity", default = 0.35f, range = 0f..1f)
        include(
            OceanBreeze(
                name = "menu_ocean",
                seed = 2_026_08_23L,
                gain = 0.42f,
                density = 0.12f,
                stereo = 0.75f,
                wind = intensity.map(0.15f, 0.85f),
                water = intensity.map(0.25f, 0.75f),
                waves = intensity.map(0.1f, 0.7f),
                chimes = intensity.map(0.02f, 0.35f),
            ),
        )
        include(PlacementClick(name = Placement.value, gain = 0.3f))
    }
}
```

Use only typed names at command sites:

```kotlin
private class GameAudio(private val audio: MiniAppAudio) {
    fun start() = audio.playMusic(MenuAudio.program)

    fun setIntensity(value: Float) = audio.setControl(MenuAudio.Intensity, value)

    fun placement() = audio.playSfx(MenuAudio.program, MenuAudio.Placement)

    fun pause() = audio.stopMusic(fadeOut = 120.ms)
}
```

Check every `AudioCommandResult`. Rejection is expected when music/SFX is disabled, the MiniApp is hidden, the session is closed, a control is out of range, or the platform backend is unavailable. Do not retry in a tight loop.

The host automatically suppresses audio with visibility/settings and closes the audio handle when the MiniApp session is destroyed. Call `stopMusic` for an intentional in-game pause or screen transition; do not construct an engine or platform player.

The exact examples above compile in `AuthorDocumentationSnippetTest`. Continue with [the DSL](kotlin-dsl.md), [shared instruments](instruments.md), [adaptive music](adaptive-music.md), [SFX recipes](sfx-recipes.md), and [budgets](performance-budgets.md).

Maintainers changing the engine should instead read [the architecture design](../../superpowers/specs/2026-08-23-kotlin-pattern-audio-design.md) and [implementation plan](../../superpowers/plans/2026-08-23-miniapp-procedural-audio.md).
