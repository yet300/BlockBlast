package ge.yet.game.blockblast.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals

class TrayGestureCleanupTest {
    @Test
    fun release_commits_once_and_does_not_cancel_in_finally() {
        val pressedChanges = mutableListOf<Boolean>()
        var commits = 0
        var cancels = 0
        val cleanup = TrayGestureCleanup(
            onPressedChange = pressedChanges::add,
            onDragCommit = { commits += 1 },
            onDragCancel = { cancels += 1 },
        )

        cleanup.markDragStarted()
        cleanup.commitDragOnce()
        cleanup.commitDragOnce()
        cleanup.finish()

        assertEquals(listOf(false), pressedChanges)
        assertEquals(1, commits)
        assertEquals(0, cancels)
    }

    @Test
    fun cancellation_cancels_once_and_never_commits() {
        val pressedChanges = mutableListOf<Boolean>()
        var commits = 0
        var cancels = 0
        val cleanup = TrayGestureCleanup(
            onPressedChange = pressedChanges::add,
            onDragCommit = { commits += 1 },
            onDragCancel = { cancels += 1 },
        )

        cleanup.markDragStarted()
        cleanup.finish()
        cleanup.commitDragOnce()
        cleanup.finish()

        assertEquals(listOf(false), pressedChanges)
        assertEquals(0, commits)
        assertEquals(1, cancels)
    }

    @Test
    fun cancellation_before_drag_only_clears_pressed_state() {
        val pressedChanges = mutableListOf<Boolean>()
        var commits = 0
        var cancels = 0
        val cleanup = TrayGestureCleanup(
            onPressedChange = pressedChanges::add,
            onDragCommit = { commits += 1 },
            onDragCancel = { cancels += 1 },
        )

        cleanup.finish()

        assertEquals(listOf(false), pressedChanges)
        assertEquals(0, commits)
        assertEquals(0, cancels)
    }
}
