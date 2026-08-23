package ge.yet.game.miniapp.audio.presets

import ge.yet.game.miniapp.audio.audioProgram
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SoundEffectsTest {
    @Test
    fun `shared sfx factories expose original bounded declarations`() {
        val program = audioProgram {
            include(PlacementClick())
            include(SuccessSweep())
            include(Explosion(seed = 42))
            include(PowerUp())
        }

        assertEquals(
            listOf("placement_click", "success_sweep", "explosion", "power_up"),
            program.soundEffects.map { it.name.value },
        )
        assertTrue(program.soundEffects.all { it.oscillators.isNotEmpty() || it.noises.isNotEmpty() })
        assertTrue(program.soundEffects.all { it.effects.size <= 4 })
        assertTrue(program.soundEffects.all { it.envelope != null })
        assertTrue(program.soundEffects.all { it.pitch != null })
        assertEquals(42, program.soundEffects.single { it.name.value == "explosion" }.noises.first().seed)
    }

    @Test
    fun `sfx names and gain are configurable without changing synthesis shape`() {
        val quiet = audioProgram { include(PlacementClick(name = "quiet_click", gain = 0.2f)) }
            .soundEffects.single()
        val loud = audioProgram { include(PlacementClick(name = "loud_click", gain = 0.8f)) }
            .soundEffects.single()

        assertEquals("quiet_click", quiet.name.value)
        assertEquals("loud_click", loud.name.value)
        assertTrue(quiet.oscillators.first().gain.value < loud.oscillators.first().gain.value)
        assertEquals(quiet.pitch, loud.pitch)
        assertNotNull(quiet.envelope)
    }
}
