package ge.yet.game.miniapp.integration

import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.testkit.MiniAppContractAssertions
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CounterAndroidResourceTest {
    @Test
    fun `Counter manifest resources resolve from the Android host`() = runTest {
        val plugin = assertNotNull(
            createCounterIntegrationGraph().registry[MiniAppId("sample.counter")],
        )

        MiniAppContractAssertions.assertResourcesResolvable(plugin.manifest)
    }
}
