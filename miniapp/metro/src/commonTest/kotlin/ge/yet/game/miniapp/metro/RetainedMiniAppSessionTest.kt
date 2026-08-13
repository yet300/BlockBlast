package ge.yet.game.miniapp.metro

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ge.yet.game.miniapp.compose.MiniAppSession
import kotlin.test.Test
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

    private class FakeSession : MiniAppSession {
        @Composable
        override fun Content(modifier: Modifier) = Unit
    }
}
