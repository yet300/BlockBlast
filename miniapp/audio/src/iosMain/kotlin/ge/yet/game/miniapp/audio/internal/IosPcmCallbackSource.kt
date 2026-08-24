package ge.yet.game.miniapp.audio.internal

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal data class IosPcmCallbackDiagnostics(
    val renderedFrames: Long = 0,
    val underrunFrames: Long = 0,
    val underrunEvents: Long = 0,
    val callbackFailures: Long = 0,
)

@OptIn(ExperimentalAtomicApi::class)
internal class IosPcmCallbackSource(
    private val ring: StereoPcmRingBuffer,
) : IosPcmBlockRenderer {
    private val callbacksInFlight = AtomicLong(0)
    private val renderedFrames = AtomicLong(0)
    private val underrunFrames = AtomicLong(0)
    private val underrunEvents = AtomicLong(0)
    private val callbackFailures = AtomicLong(0)

    override fun render(left: FloatArray, right: FloatArray, frameCount: Int) {
        callbacksInFlight.fetchAndAdd(1)
        try {
            val missing = ring.readOrSilence(left, right, frameCount)
            renderedFrames.fetchAndAdd((frameCount - missing).toLong())
            if (missing != 0) {
                underrunFrames.fetchAndAdd(missing.toLong())
                underrunEvents.fetchAndAdd(1)
            }
        } finally {
            callbacksInFlight.fetchAndAdd(-1)
        }
    }

    override fun recordCallbackFailure() {
        callbackFailures.fetchAndAdd(1)
    }

    fun hasCallbackInFlight(): Boolean = callbacksInFlight.load() != 0L

    fun drainDiagnostics(): IosPcmCallbackDiagnostics = IosPcmCallbackDiagnostics(
        renderedFrames = renderedFrames.exchange(0),
        underrunFrames = underrunFrames.exchange(0),
        underrunEvents = underrunEvents.exchange(0),
        callbackFailures = callbackFailures.exchange(0),
    )
}
