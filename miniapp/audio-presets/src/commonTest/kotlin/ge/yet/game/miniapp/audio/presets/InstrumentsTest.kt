package ge.yet.game.miniapp.audio.presets

import ge.yet.game.miniapp.audio.audioProgram
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstrumentsTest {
    @Test
    fun `shared instrument factories produce distinct bounded declarations`() {
        val program = audioProgram {
            include(SoftPad())
            include(ChipLead())
            include(AnalogBass())
            include(GlassBell())
        }

        assertEquals(
            listOf("soft_pad", "chip_lead", "analog_bass", "glass_bell"),
            program.instruments.map { it.name.value },
        )
        assertTrue(program.instruments.all { it.oscillators.isNotEmpty() })
        assertTrue(program.instruments.all { it.oscillators.size <= 8 && it.effects.size <= 4 })
        assertTrue(program.instruments.single { it.name.value == "glass_bell" }.partials.size >= 3)
    }

    @Test
    fun `instrument gain parameter changes only source gains`() {
        val quiet = audioProgram { include(SoftPad(gain = 0.2f)) }.instruments.single()
        val loud = audioProgram { include(SoftPad(gain = 0.8f)) }.instruments.single()

        assertTrue(quiet.oscillators.zip(loud.oscillators).all { (a, b) -> a.gain.value < b.gain.value })
        assertEquals(quiet.filters, loud.filters)
        assertEquals(quiet.envelope, loud.envelope)
    }
}
