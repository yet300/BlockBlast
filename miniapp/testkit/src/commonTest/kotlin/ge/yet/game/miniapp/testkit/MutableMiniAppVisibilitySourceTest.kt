package ge.yet.game.miniapp.testkit

import ge.yet.game.miniapp.api.MiniAppVisibility
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MutableMiniAppVisibilitySourceTest {
    @Test
    fun `publishes every visibility transition in order`() = runTest {
        val source = MutableMiniAppVisibilitySource()
        val observed = mutableListOf<MiniAppVisibility>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            source.visibility.take(4).toList(observed)
        }

        source.set(MiniAppVisibility.OBSCURED)
        runCurrent()
        source.set(MiniAppVisibility.BACKGROUND)
        runCurrent()
        source.set(MiniAppVisibility.ACTIVE)
        runCurrent()
        collector.join()

        assertEquals(
            listOf(
                MiniAppVisibility.ACTIVE,
                MiniAppVisibility.OBSCURED,
                MiniAppVisibility.BACKGROUND,
                MiniAppVisibility.ACTIVE,
            ),
            observed,
        )
    }
}
