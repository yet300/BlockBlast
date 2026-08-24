package ge.yet.game.miniapp.audio.internal

import platform.Foundation.NSCondition
import platform.Foundation.NSDate
import platform.Foundation.NSThread

internal interface IosProducerThread {
    fun start(block: () -> Unit)
    fun signal()
    fun awaitSignal(timeoutSeconds: Double)
    fun awaitTermination(timeoutSeconds: Double): Boolean
    fun markTerminated()
}

internal fun interface IosProducerThreadFactory {
    fun create(): IosProducerThread
}

internal class FoundationIosProducerThread : IosProducerThread {
    private val condition = NSCondition()
    private var started = false
    private var terminated = false
    private var signalVersion = 0L
    private var consumedSignalVersion = 0L

    override fun start(block: () -> Unit) {
        condition.lock()
        try {
            check(!started)
            started = true
        } finally {
            condition.unlock()
        }
        NSThread.detachNewThreadWithBlock {
            try {
                block()
            } finally {
                markTerminated()
            }
        }
    }

    override fun signal() {
        condition.lock()
        try {
            signalVersion += 1
            condition.broadcast()
        } finally {
            condition.unlock()
        }
    }

    override fun awaitSignal(timeoutSeconds: Double) {
        val deadline = deadlineAfter(timeoutSeconds)
        condition.lock()
        try {
            while (!terminated && consumedSignalVersion == signalVersion) {
                if (!condition.waitUntilDate(deadline)) return
            }
            consumedSignalVersion = signalVersion
        } finally {
            condition.unlock()
        }
    }

    override fun awaitTermination(timeoutSeconds: Double): Boolean {
        val deadline = deadlineAfter(timeoutSeconds)
        condition.lock()
        return try {
            while (!terminated) {
                if (!condition.waitUntilDate(deadline)) return false
            }
            true
        } finally {
            condition.unlock()
        }
    }

    override fun markTerminated() {
        condition.lock()
        try {
            terminated = true
            condition.broadcast()
        } finally {
            condition.unlock()
        }
    }
}

private fun deadlineAfter(timeoutSeconds: Double): NSDate = NSDate(
    timeIntervalSinceReferenceDate = NSDate().timeIntervalSinceReferenceDate + timeoutSeconds,
)
