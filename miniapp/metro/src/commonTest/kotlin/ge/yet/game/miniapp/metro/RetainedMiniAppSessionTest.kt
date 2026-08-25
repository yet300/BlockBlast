package ge.yet.game.miniapp.metro

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import ge.yet.game.miniapp.compose.MiniAppFrameMode
import ge.yet.game.miniapp.compose.MiniAppSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RetainedMiniAppSessionTest {

    @Test
    fun handle_keeps_the_original_graph() {
        val graph = Any()
        val handle = RetainedMiniAppSession(graph, FakeSession())

        assertTrue(handle.graph === graph)
    }

    @Test
    fun handles_for_distinct_graphs_remain_distinct() {
        val firstGraph = Any()
        val secondGraph = Any()
        val firstHandle = RetainedMiniAppSession(firstGraph, FakeSession())
        val secondHandle = RetainedMiniAppSession(secondGraph, FakeSession())

        assertTrue(firstHandle.graph !== secondHandle.graph)
        assertTrue(firstHandle !== secondHandle)
    }

    @Test
    fun frame_mode_is_the_delegate_decompose_value() {
        val mode = MutableValue(MiniAppFrameMode.ContentOnly)
        val handle = RetainedMiniAppSession(Any(), FakeSession(mode))

        assertSame(mode, handle.frameMode)
    }

    @Test
    fun handle_back_delegates_exactly_once() {
        val delegate = FakeSession(backResponses = ArrayDeque(listOf(true, false)))
        val handle = RetainedMiniAppSession(Any(), delegate)

        assertTrue(handle.handleBack())
        assertFalse(handle.handleBack())
        assertEquals(2, delegate.handleBackCount)
    }

    private class FakeSession(
        override val frameMode: Value<MiniAppFrameMode> =
            MutableValue(MiniAppFrameMode.Standard),
        private val backResponses: ArrayDeque<Boolean> = ArrayDeque(),
    ) : MiniAppSession {
        var handleBackCount = 0
            private set

        override fun handleBack(): Boolean {
            handleBackCount += 1
            return backResponses.removeFirstOrNull() ?: false
        }

        @Composable
        override fun Content(modifier: Modifier) = Unit
    }
}
