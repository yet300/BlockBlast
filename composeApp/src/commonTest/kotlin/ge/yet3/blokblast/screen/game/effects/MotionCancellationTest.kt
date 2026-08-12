package ge.yet3.blokblast.screen.game.effects

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MotionCancellationTest {

    @Test
    fun cancelling_stripe_sweep_clears_active_coordinates() = runMotionTest {
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
    fun newer_stripe_sweep_keeps_ownership_after_previous_sweep_unwinds() = runMotionTest {
        val state = ComboStripesState()
        val first = launch {
            state.sweep(rows = listOf(1), cols = listOf(2), durationMillis = 10_000)
        }
        runCurrent()

        assertEquals(listOf(1), state.activeRows)
        assertEquals(listOf(2), state.activeCols)
        assertTrue(state.progress.isRunning)

        val second = launch {
            state.sweep(rows = listOf(6), cols = listOf(7), durationMillis = 10_000)
        }
        runCurrent()
        first.join()

        assertTrue(first.isCompleted)
        assertEquals(listOf(6), state.activeRows)
        assertEquals(listOf(7), state.activeCols)
        assertTrue(second.isActive)
        assertTrue(state.progress.isRunning)

        advanceTimeBy(32)
        runCurrent()

        assertTrue(state.progress.value > 0f)
        assertEquals(listOf(6), state.activeRows)
        assertEquals(listOf(7), state.activeCols)

        second.cancelAndJoin()
    }

    @Test
    fun cancelling_glitch_resets_intensity() = runMotionTest {
        val state = GlitchState()
        val glitch = launch(start = CoroutineStart.UNDISPATCHED) {
            state.trigger(durationMillis = 10_000)
        }

        assertEquals(1f, state.intensity.value)

        glitch.cancelAndJoin()

        assertEquals(0f, state.intensity.value)
    }

    @Test
    fun newer_glitch_trigger_keeps_ownership_after_previous_trigger_unwinds() = runMotionTest {
        val state = GlitchState()
        val first = launch {
            state.trigger(durationMillis = 10_000)
        }
        runCurrent()

        assertTrue(state.intensity.isRunning)

        val second = launch {
            state.trigger(durationMillis = 10_000)
        }
        runCurrent()
        first.join()

        assertTrue(first.isCompleted)
        assertTrue(second.isActive)
        assertTrue(state.intensity.isRunning)
        assertTrue(state.intensity.value > 0f)

        val initialIntensity = state.intensity.value
        advanceTimeBy(32)
        runCurrent()

        assertTrue(state.intensity.value in 0f..<initialIntensity)
        assertTrue(second.isActive)

        second.cancelAndJoin()
        assertEquals(0f, state.intensity.value)
    }

    @Test
    fun cancelling_particle_burst_removes_all_particles() = runMotionTest {
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
    fun cancelling_shockwave_removes_the_draw_entry() = runMotionTest {
        val state = ParticleBurstState()
        val shockwave = launch(start = CoroutineStart.UNDISPATCHED) {
            state.shockwave(cellGridX = 3, cellGridY = 4, color = Color.White)
        }

        assertEquals(1, state.shockwaves.size)

        shockwave.cancelAndJoin()

        assertTrue(state.shockwaves.isEmpty())
    }
}

private fun runMotionTest(testBody: suspend TestScope.() -> Unit) =
    TestCoroutineScheduler().let { scheduler ->
        runTest(
            context = StandardTestDispatcher(scheduler) + TestMonotonicFrameClock(scheduler),
            testBody = testBody,
        )
    }

@OptIn(ExperimentalCoroutinesApi::class)
private class TestMonotonicFrameClock(
    private val scheduler: TestCoroutineScheduler,
) : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
        delay(TEST_FRAME_DURATION_MILLIS)
        return onFrame(scheduler.currentTime * NANOSECONDS_PER_MILLISECOND)
    }
}

private const val TEST_FRAME_DURATION_MILLIS = 16L
private const val NANOSECONDS_PER_MILLISECOND = 1_000_000L
