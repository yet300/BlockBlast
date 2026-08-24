package ge.yet.game.miniapp.testkit

import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlin.test.Test
import kotlin.test.assertEquals

class MiniAppLifecycleHarnessTest {
    @Test
    fun `resumed lifecycle invokes destroy callback exactly once`() {
        val harness = MiniAppLifecycleHarness()
        var destroyCount = 0
        harness.componentContext.lifecycle.doOnDestroy { destroyCount += 1 }

        harness.resume()
        harness.stop()
        harness.destroy()
        harness.destroy()

        assertEquals(1, destroyCount)
    }
}
