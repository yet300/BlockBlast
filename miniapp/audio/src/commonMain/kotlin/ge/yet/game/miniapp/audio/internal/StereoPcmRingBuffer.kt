package ge.yet.game.miniapp.audio.internal

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class StereoPcmRingBuffer(
    val capacityFrames: Int,
) {
    private val mask: Int
    private val leftSamples: FloatArray
    private val rightSamples: FloatArray
    private val readPosition = AtomicLong(0)
    private val writePosition = AtomicLong(0)

    val availableFrames: Int
        get() = (writePosition.load() - readPosition.load()).toInt()

    val freeFrames: Int
        get() = capacityFrames - availableFrames

    init {
        require(capacityFrames > 0 && capacityFrames and (capacityFrames - 1) == 0)
        mask = capacityFrames - 1
        leftSamples = FloatArray(capacityFrames)
        rightSamples = FloatArray(capacityFrames)
    }

    fun write(left: FloatArray, right: FloatArray, frameCount: Int): Int {
        val currentWrite = writePosition.load()
        val start = (currentWrite and mask.toLong()).toInt()
        val writable = minOf(frameCount, capacityFrames - (currentWrite - readPosition.load()).toInt())
        val first = minOf(writable, capacityFrames - start)
        left.copyInto(leftSamples, start, 0, first)
        right.copyInto(rightSamples, start, 0, first)
        val second = writable - first
        if (second != 0) {
            left.copyInto(leftSamples, 0, first, writable)
            right.copyInto(rightSamples, 0, first, writable)
        }
        writePosition.store(currentWrite + writable)
        return writable
    }

    fun readOrSilence(left: FloatArray, right: FloatArray, frameCount: Int): Int {
        val currentRead = readPosition.load()
        val start = (currentRead and mask.toLong()).toInt()
        val readable = minOf(frameCount, (writePosition.load() - currentRead).toInt())
        val first = minOf(readable, capacityFrames - start)
        leftSamples.copyInto(left, 0, start, start + first)
        rightSamples.copyInto(right, 0, start, start + first)
        val second = readable - first
        if (second != 0) {
            leftSamples.copyInto(left, first, 0, second)
            rightSamples.copyInto(right, first, 0, second)
        }
        left.fill(0f, readable, frameCount)
        right.fill(0f, readable, frameCount)
        readPosition.store(currentRead + readable)
        return frameCount - readable
    }

    fun reset() {
        readPosition.store(0)
        writePosition.store(0)
    }
}
