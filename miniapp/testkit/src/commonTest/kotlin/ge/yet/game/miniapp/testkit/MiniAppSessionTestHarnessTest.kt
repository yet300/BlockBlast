package ge.yet.game.miniapp.testkit

import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MiniAppSessionTestHarnessTest {
    @Test
    fun `harness exposes one context and owns lifecycle teardown`() {
        val harness = MiniAppSessionTestHarness()
        var destroyCount = 0
        harness.context.componentContext.lifecycle.doOnDestroy { destroyCount += 1 }

        harness.resume()
        harness.stop()
        harness.destroy()
        harness.destroy()

        assertEquals(1, destroyCount)
        assertIs<MutableMiniAppStorage>(harness.context.storage)
        assertIs<RecordingMiniAppSessionHost>(harness.context.host)
    }

    @Test
    fun `with harness always destroys the session context`() {
        var destroyCount = 0

        withMiniAppSession { harness ->
            harness.context.componentContext.lifecycle.doOnDestroy { destroyCount += 1 }
            harness.resume()
        }

        assertEquals(1, destroyCount)
    }
}
