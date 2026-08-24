package ge.yet.game.miniapp.integration

import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.testkit.MiniAppContractAssertions
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

class CounterIosResourceTest {
    @Test
    fun `Counter manifest resources resolve from the iOS host`() = runTest {
        val plugin = assertNotNull(
            createCounterIntegrationGraph().registry[MiniAppId("sample.counter")],
        )

        MiniAppContractAssertions.assertResourcesResolvable(plugin.manifest)
    }
}
