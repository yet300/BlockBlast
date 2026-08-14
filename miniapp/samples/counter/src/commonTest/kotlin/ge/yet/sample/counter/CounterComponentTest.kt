package ge.yet.sample.counter

import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import kotlin.test.Test
import kotlin.test.assertEquals

class CounterComponentTest {
    @Test
    fun `counter starts at zero and increments from current model`() {
        val setup = createComponent()

        assertEquals(CounterComponent.Model(count = 0), setup.component.model.value)

        setup.component.onIncrementClicked()
        setup.component.onIncrementClicked()

        assertEquals(CounterComponent.Model(count = 2), setup.component.model.value)
    }

    @Test
    fun `component exposes the session visibility source`() {
        val setup = createComponent()

        assertEquals(MiniAppVisibility.ACTIVE, setup.component.visibility.value)
        setup.visibility.set(MiniAppVisibility.OBSCURED)
        assertEquals(MiniAppVisibility.OBSCURED, setup.component.visibility.value)
        setup.visibility.set(MiniAppVisibility.BACKGROUND)
        assertEquals(MiniAppVisibility.BACKGROUND, setup.component.visibility.value)
    }

    @Test
    fun `resumed component records lifecycle destruction exactly once`() {
        val setup = createComponent()

        setup.lifecycle.resume()
        setup.lifecycle.stop()
        setup.lifecycle.destroy()
        setup.lifecycle.destroy()

        assertEquals(1, setup.component.destroyCount)
    }

    private fun createComponent(): Setup {
        val lifecycle = MiniAppLifecycleHarness()
        val visibility = MutableMiniAppVisibilitySource()
        return Setup(
            component = DefaultCounterComponent(
                componentContext = lifecycle.componentContext,
                visibilitySource = visibility,
            ),
            lifecycle = lifecycle,
            visibility = visibility,
        )
    }

    private data class Setup(
        val component: DefaultCounterComponent,
        val lifecycle: MiniAppLifecycleHarness,
        val visibility: MutableMiniAppVisibilitySource,
    )
}
