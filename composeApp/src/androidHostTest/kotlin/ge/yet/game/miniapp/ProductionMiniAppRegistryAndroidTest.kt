package ge.yet.game.miniapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.zacsweers.metro.createGraphFactory
import ge.yet.game.di.AndroidAppGraph
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.bundle.ProductionMiniAppExpectation
import ge.yet.game.miniapp.testkit.MiniAppContractAssertions
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProductionMiniAppRegistryAndroidTest {
    @Test
    fun `production registry contains exactly the configured mini-app on Android`() = runTest {
        val graph = createGraphFactory<AndroidAppGraph.Factory>().create(
            ApplicationProvider.getApplicationContext<Context>(),
        )
        val expected = ProductionMiniAppExpectation().expectedIds
        val registry = graph.miniAppRegistry

        assertEquals(expected, registry.manifests.mapTo(mutableSetOf()) { it.id })
        val productionPlugin = assertNotNull(registry[expected.single()])
        assertNull(registry[MiniAppId("sample.counter")])
        MiniAppContractAssertions.assertResourcesResolvable(productionPlugin.manifest)
    }
}
