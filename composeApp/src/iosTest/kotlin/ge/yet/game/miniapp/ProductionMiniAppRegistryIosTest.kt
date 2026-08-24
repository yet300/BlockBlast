package ge.yet.game.miniapp

import ge.yet.game.di.getNativeAppGraph
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.bundle.ProductionMiniAppExpectation
import ge.yet.game.miniapp.testkit.MiniAppContractAssertions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProductionMiniAppRegistryIosTest {
    @Test
    fun `production registry contains exactly the configured mini-app on iOS`() = runTest {
        val graph = getNativeAppGraph()
        val expected = ProductionMiniAppExpectation().expectedIds
        val registry = graph.miniAppRegistry

        assertEquals(expected, registry.manifests.mapTo(mutableSetOf()) { it.id })
        val productionPlugin = assertNotNull(registry[expected.single()])
        assertNull(registry[MiniAppId("sample.counter")])
        MiniAppContractAssertions.assertResourcesResolvable(productionPlugin.manifest)
    }
}
