package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.audio.AudioControlDeclaration
import ge.yet.game.miniapp.audio.AudioControlName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RealtimeControlPositionsTest {
    @Test
    fun `fixed control storage resets and updates without growing`() {
        val controls = RealtimeControlPositions(capacity = 2)
        val intensity = declaration("intensity", default = 0.25f)
        val danger = declaration("danger", default = 0.75f)

        assertTrue(controls.reset(listOf(intensity, danger)))
        assertEquals(2, controls.size)
        assertEquals(0.25f, controls[intensity.name])
        assertTrue(controls.set(intensity.name, 0.9f))
        assertEquals(0.9f, controls[intensity.name])

        assertTrue(controls.reset(listOf(danger)))
        assertEquals(1, controls.size)
        assertEquals(null, controls[intensity.name])
        assertFalse(controls.reset(listOf(intensity, danger, declaration("speed", 0.5f))))
        assertEquals(2, controls.capacity)
    }

    private fun declaration(name: String, default: Float) = AudioControlDeclaration(
        name = AudioControlName(name),
        default = default,
        range = 0f..1f,
    )
}
