package ge.yet3.blokblast.screen.game.effects

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MotionCancellationTest {

    @Test
    fun cancelling_stripe_sweep_clears_active_coordinates() = runTest {
        val state = ComboStripesState()
        val sweep = launch(start = CoroutineStart.UNDISPATCHED) {
            state.sweep(rows = listOf(2), cols = listOf(5), durationMillis = 10_000)
        }

        assertEquals(listOf(2), state.activeRows)
        assertEquals(listOf(5), state.activeCols)

        sweep.cancelAndJoin()

        assertTrue(state.activeRows.isEmpty())
        assertTrue(state.activeCols.isEmpty())
    }

    @Test
    fun cancelling_glitch_resets_intensity() = runTest {
        val state = GlitchState()
        val glitch = launch(start = CoroutineStart.UNDISPATCHED) {
            state.trigger(durationMillis = 10_000)
        }

        assertEquals(1f, state.intensity.value)

        glitch.cancelAndJoin()

        assertEquals(0f, state.intensity.value)
    }

    @Test
    fun cancelling_particle_burst_removes_all_particles() = runTest {
        val state = ParticleBurstState()
        val burst = launch {
            state.burst(cellGridX = 3, cellGridY = 4, color = Color.Red, count = 4)
        }
        runCurrent()

        assertEquals(4, state.particles.size)

        burst.cancelAndJoin()

        assertTrue(state.particles.isEmpty())
    }

    @Test
    fun cancelling_shockwave_removes_the_draw_entry() = runTest {
        val state = ParticleBurstState()
        val shockwave = launch(start = CoroutineStart.UNDISPATCHED) {
            state.shockwave(cellGridX = 3, cellGridY = 4, color = Color.White)
        }

        assertEquals(1, state.shockwaves.size)

        shockwave.cancelAndJoin()

        assertTrue(state.shockwaves.isEmpty())
    }
}
