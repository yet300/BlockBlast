package ge.yet.game.miniapp.audio.internal

internal enum class AudioCommandOfferResult {
    Accepted,
    AcceptedAfterEviction,
    Coalesced,
    RejectedFull,
}

/**
 * Allocation-free after construction. Access is thread-confined; the engine owns the
 * producer/consumer hand-off rather than adding a lock to the realtime render path.
 */
internal class AudioCommandQueue(
    private val capacity: Int,
) {
    private val commands: Array<AudioCommand?>
    private var head = 0

    var size: Int = 0
        private set

    init {
        require(capacity >= 2)
        commands = arrayOfNulls(capacity)
    }

    fun offer(command: AudioCommand): AudioCommandOfferResult {
        coalesce(command)?.let { return it }

        if (size < capacity) {
            append(command)
            return AudioCommandOfferResult.Accepted
        }
        if (!command.isCritical) return AudioCommandOfferResult.RejectedFull

        val droppableIndex = (0 until size).firstOrNull { !commandAt(it).isCritical }
            ?: error("A full critical-only queue must contain a coalescible command")
        removeAt(droppableIndex)
        append(command)
        return AudioCommandOfferResult.AcceptedAfterEviction
    }

    fun poll(): AudioCommand? {
        if (size == 0) return null
        val command = commands[head] ?: error("Queue slot is unexpectedly empty")
        commands[head] = null
        head = (head + 1) % capacity
        size -= 1
        if (size == 0) head = 0
        return command
    }

    private fun coalesce(command: AudioCommand): AudioCommandOfferResult? = when (command) {
        is AudioCommand.SetControl -> coalesceTrailingControl(command)
        is AudioCommand.StopMusic -> coalesceCritical(command) { it is AudioCommand.StopMusic }
        AudioCommand.Destroy -> coalesceCritical(command) { it === AudioCommand.Destroy }
        is AudioCommand.PlayMusic,
        is AudioCommand.PlaySfx,
        -> null
    }

    private fun coalesceTrailingControl(command: AudioCommand.SetControl): AudioCommandOfferResult? {
        for (logicalIndex in size - 1 downTo 0) {
            val queued = commandAt(logicalIndex)
            if (queued !is AudioCommand.SetControl) return null
            if (queued.name == command.name) {
                commands[physicalIndex(logicalIndex)] = command
                return AudioCommandOfferResult.Coalesced
            }
        }
        return null
    }

    private inline fun coalesceCritical(
        replacement: AudioCommand,
        matches: (AudioCommand) -> Boolean,
    ): AudioCommandOfferResult? {
        for (logicalIndex in 0 until size) {
            if (matches(commandAt(logicalIndex))) {
                commands[physicalIndex(logicalIndex)] = replacement
                return AudioCommandOfferResult.Coalesced
            }
        }
        return null
    }

    private fun append(command: AudioCommand) {
        commands[physicalIndex(size)] = command
        size += 1
    }

    private fun removeAt(logicalIndex: Int) {
        require(logicalIndex in 0 until size)
        for (index in logicalIndex until size - 1) {
            commands[physicalIndex(index)] = commands[physicalIndex(index + 1)]
        }
        commands[physicalIndex(size - 1)] = null
        size -= 1
    }

    private fun commandAt(logicalIndex: Int): AudioCommand =
        commands[physicalIndex(logicalIndex)] ?: error("Queue slot is unexpectedly empty")

    private fun physicalIndex(logicalIndex: Int): Int = (head + logicalIndex) % capacity
}

private val AudioCommand.isCritical: Boolean
    get() = when (this) {
        is AudioCommand.StopMusic,
        AudioCommand.Destroy,
        -> true
        is AudioCommand.PlayMusic,
        is AudioCommand.PlaySfx,
        is AudioCommand.SetControl,
        -> false
    }
