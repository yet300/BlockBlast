package ge.yet.game.miniapp.testkit

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop

class MiniAppLifecycleHarness {
    val lifecycle = LifecycleRegistry()
    val componentContext: ComponentContext = DefaultComponentContext(lifecycle = lifecycle)

    fun resume() {
        lifecycle.resume()
    }

    fun stop() {
        lifecycle.stop()
    }

    fun destroy() {
        lifecycle.destroy()
    }
}
