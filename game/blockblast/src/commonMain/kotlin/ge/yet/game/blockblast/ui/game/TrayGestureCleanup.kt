package ge.yet.game.blockblast.ui.game

/**
 * Idempotent cleanup for one accepted tray gesture.
 *
 * Pointer-input coroutines can be cancelled by node detach or input-key
 * changes, so cleanup must not depend on receiving a Release event.
 */
internal class TrayGestureCleanup(
    private val onPressedChange: (Boolean) -> Unit,
    private val onDragCommit: () -> Unit,
    private val onDragCancel: () -> Unit,
) {
    private var dragStarted = false
    private var terminalOutcome: TerminalOutcome? = null
    private var finished = false

    val isDragging: Boolean
        get() = dragStarted && terminalOutcome == null

    fun markDragStarted() {
        if (!finished) dragStarted = true
    }

    fun commitDragOnce() {
        if (!dragStarted || terminalOutcome != null) return
        terminalOutcome = TerminalOutcome.Committed
        onDragCommit()
    }

    fun finish() {
        if (finished) return
        finished = true
        try {
            onPressedChange(false)
        } finally {
            if (dragStarted && terminalOutcome == null) {
                terminalOutcome = TerminalOutcome.Cancelled
                onDragCancel()
            }
        }
    }

    private enum class TerminalOutcome {
        Committed,
        Cancelled,
    }
}
