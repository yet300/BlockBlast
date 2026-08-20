package ge.yet.game.miniapp.integration

import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.testkit.MiniAppContractAssertions
import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import ge.yet.game.miniapp.testkit.RecordingMiniAppSessionHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class CounterRegistryIntegrationTest {
    private val counterId = MiniAppId("sample.counter")

    @Test
    fun `Metro aggregates exactly the Counter plugin`() {
        val registry = createCounterIntegrationGraph().registry

        assertEquals(setOf(counterId), registry.manifests.map { it.id }.toSet())
        MiniAppContractAssertions.assertSinglePlugin(registry, counterId)
    }

    @Test
    fun `Counter metadata is available before any session inputs exist`() {
        val registry = createCounterIntegrationGraph().registry
        val plugin = assertNotNull(registry[counterId])

        MiniAppContractAssertions.assertManifest(plugin, counterId)
        assertEquals(plugin.manifest, registry.manifests.single())
    }

    @Test
    fun `Counter creates a retained child graph session`() {
        val plugin = assertNotNull(createCounterIntegrationGraph().registry[counterId])
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }

        try {
            val session = plugin.createSession(
                componentContext = lifecycle.componentContext,
                visibility = MutableMiniAppVisibilitySource(),
                host = RecordingMiniAppSessionHost(),
            )

            MiniAppContractAssertions.assertRetainedGraphSession(session)
        } finally {
            lifecycle.destroy()
        }
    }

    @Test
    fun `destroying one session lifecycle leaves live and future child graphs independent`() {
        val plugin = assertNotNull(createCounterIntegrationGraph().registry[counterId])
        var firstLifecycle: MiniAppLifecycleHarness? = null
        var secondLifecycle: MiniAppLifecycleHarness? = null
        var thirdLifecycle: MiniAppLifecycleHarness? = null

        try {
            val activeFirstLifecycle = MiniAppLifecycleHarness()
            firstLifecycle = activeFirstLifecycle
            activeFirstLifecycle.resume()
            val activeSecondLifecycle = MiniAppLifecycleHarness()
            secondLifecycle = activeSecondLifecycle
            activeSecondLifecycle.resume()
            val firstSession = plugin.createSession(
                componentContext = activeFirstLifecycle.componentContext,
                visibility = MutableMiniAppVisibilitySource(),
                host = RecordingMiniAppSessionHost(),
            )
            val secondSession = plugin.createSession(
                componentContext = activeSecondLifecycle.componentContext,
                visibility = MutableMiniAppVisibilitySource(),
                host = RecordingMiniAppSessionHost(),
            )

            assertNotSame(firstSession, secondSession)
            MiniAppContractAssertions.assertRetainedGraphSession(firstSession)
            MiniAppContractAssertions.assertRetainedGraphSession(secondSession)

            activeFirstLifecycle.destroy()

            MiniAppContractAssertions.assertRetainedGraphSession(secondSession)
            val activeThirdLifecycle = MiniAppLifecycleHarness()
            thirdLifecycle = activeThirdLifecycle
            activeThirdLifecycle.resume()
            val thirdSession = plugin.createSession(
                componentContext = activeThirdLifecycle.componentContext,
                visibility = MutableMiniAppVisibilitySource(),
                host = RecordingMiniAppSessionHost(),
            )
            assertNotSame(firstSession, thirdSession)
            assertNotSame(secondSession, thirdSession)
            MiniAppContractAssertions.assertRetainedGraphSession(thirdSession)
        } finally {
            thirdLifecycle?.destroy()
            secondLifecycle?.destroy()
            firstLifecycle?.destroy()
        }
    }
}
