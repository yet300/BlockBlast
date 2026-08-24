package ge.yet.game.miniapp.audio.internal

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import platform.Foundation.NSQualityOfServiceUserInitiated
import platform.Foundation.NSThread

@OptIn(ExperimentalAtomicApi::class)
class IosProducerThreadTest {
    @Test
    fun `foundation worker runs at user initiated quality of service`() {
        val worker = FoundationIosProducerThread()
        val observedQualityOfService = AtomicInt(Int.MIN_VALUE)

        worker.start {
            observedQualityOfService.store(NSThread.currentThread.qualityOfService.toInt())
        }

        assertTrue(worker.awaitTermination(timeoutSeconds = 1.0))
        assertEquals(NSQualityOfServiceUserInitiated.toInt(), observedQualityOfService.load())
    }

    @Test
    fun `foundation worker starts wakes and terminates within deadline`() {
        val worker = FoundationIosProducerThread()
        val phase = AtomicInt(0)
        worker.start {
            phase.store(1)
            worker.awaitSignal(timeoutSeconds = 1.0)
            phase.store(2)
        }
        assertTrue(waitUntil(timeoutSeconds = 1.0) { phase.load() == 1 })

        worker.signal()

        assertTrue(worker.awaitTermination(timeoutSeconds = 1.0))
        assertEquals(2, phase.load())
    }

    private fun waitUntil(timeoutSeconds: Double, predicate: () -> Boolean): Boolean {
        val attempts = (timeoutSeconds / POLL_SECONDS).toInt()
        repeat(attempts) {
            if (predicate()) return true
            NSThread.sleepForTimeInterval(POLL_SECONDS)
        }
        return predicate()
    }
}

private const val POLL_SECONDS = 0.001
