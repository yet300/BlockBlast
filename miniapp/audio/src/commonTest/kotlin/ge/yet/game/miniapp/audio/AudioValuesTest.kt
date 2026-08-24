package ge.yet.game.miniapp.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AudioValuesTest {
    @Test
    fun `author units create validated domain values`() {
        assertEquals(0.008, 8.ms.seconds)
        assertEquals(2.5, 2.5.seconds.seconds)
        assertEquals(440.0, 440.hz.value)
        assertEquals(0.25f, Gain.of(0.25f).value)
        assertEquals(-0.5f, Pan.of(-0.5f).value)
        assertEquals(112f, Tempo.of(112f).bpm)
        assertEquals(60, MidiNote.of(60).value)
    }

    @Test
    fun `non finite or out of range author values are rejected`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1).forEach { value ->
            assertFailsWith<IllegalArgumentException> { AudioDuration.seconds(value) }
        }
        assertFailsWith<IllegalArgumentException> { Frequency.hz(0.0) }
        assertFailsWith<IllegalArgumentException> { Gain.of(-0.01f) }
        assertFailsWith<IllegalArgumentException> { Gain.of(4.01f) }
        assertFailsWith<IllegalArgumentException> { Pan.of(1.01f) }
        assertFailsWith<IllegalArgumentException> { Tempo.of(19.9f) }
        assertFailsWith<IllegalArgumentException> { Tempo.of(400.1f) }
        assertFailsWith<IllegalArgumentException> { MidiNote.of(128) }
    }

    @Test
    fun `declaration names use stable lowercase author syntax`() {
        assertEquals("intensity", AudioControlName("intensity").value)
        assertEquals("analog_bass", InstrumentName("analog_bass").value)
        assertEquals("place2", SfxName("place2").value)

        listOf("", "AnalogBass", "analog-bass", "2bass", "bass.line").forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { AudioControlName(invalid) }
            assertFailsWith<IllegalArgumentException> { InstrumentName(invalid) }
            assertFailsWith<IllegalArgumentException> { SfxName(invalid) }
        }
    }
}
