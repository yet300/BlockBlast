package ge.yet.sample.counter

import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.audio.audioProgram
import ge.yet.game.miniapp.audio.presets.Explosion
import ge.yet.game.miniapp.audio.presets.OceanBreeze
import ge.yet.game.miniapp.audio.presets.PlacementClick
import ge.yet.game.miniapp.audio.presets.PowerUp
import ge.yet.game.miniapp.audio.presets.SuccessSweep

/** A compact, asset-free example contributors can copy into a MiniApp. */
internal object CounterAudio {
    val Intensity = AudioControlName("intensity")

    val program = audioProgram {
        tempo(84f)
        val intensity = control(
            name = Intensity.value,
            default = CounterComponent.DEFAULT_INTENSITY,
            range = 0f..1f,
        )
        include(
            OceanBreeze(
                name = "counter_ocean",
                seed = 2_026_08_23L,
                wind = intensity.map(0.12f, 0.9f),
                water = intensity.map(0.2f, 0.85f),
                waves = intensity.map(0.1f, 0.8f),
                chimes = intensity.map(0.05f, 0.45f),
            ),
        )
        include(PlacementClick())
        include(SuccessSweep())
        include(Explosion())
        include(PowerUp())
    }

    fun sfxName(effect: CounterComponent.SoundEffect): SfxName = when (effect) {
        CounterComponent.SoundEffect.PLACEMENT -> SfxName("placement_click")
        CounterComponent.SoundEffect.SUCCESS -> SfxName("success_sweep")
        CounterComponent.SoundEffect.EXPLOSION -> SfxName("explosion")
        CounterComponent.SoundEffect.POWER_UP -> SfxName("power_up")
    }
}
