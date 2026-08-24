package ge.yet.game.miniapp.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext
import ge.yet.game.miniapp.api.MiniAppCategoryId
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.StringResource

@OptIn(InternalResourceApi::class)
class MiniAppContractsTest {

    @Test
    fun `sessions use the standard host frame by default`() {
        val session = object : MiniAppSession {
            @Composable
            override fun Content(modifier: Modifier) = Unit
        }

        assertEquals(MiniAppFrameMode.Standard, session.frameMode.value)
    }

    @Test
    fun `reading plugin metadata does not create a session`() {
        val plugin = FakePlugin()

        assertEquals(MiniAppId("game.fake"), plugin.manifest.id)

        assertEquals(1, plugin.manifestReadCount)
        assertEquals(0, plugin.sessionCreateCount)
    }

    @Test
    fun `interstitial gate request completes immediately when no ad will show`() {
        var completed = false

        MiniAppInterstitialGate(willShowAd = false) { onComplete -> onComplete() }
            .request { completed = true }

        assertTrue(completed)
    }

    private class FakePlugin : MiniAppPlugin {
        var manifestReadCount = 0
        var sessionCreateCount = 0

        override val manifest: MiniAppManifest
            get() {
                manifestReadCount += 1
                return MiniAppManifest(
                    id = MiniAppId("game.fake"),
                    title = StringResource("fake_title", "fake_title", emptySet()),
                    description = StringResource("fake_description", "fake_description", emptySet()),
                    icon = DrawableResource("fake_icon", emptySet()),
                    cover = null,
                    category = MiniAppCategoryId("game"),
                    sortPriority = 0,
                )
            }

        override fun createSession(context: MiniAppSessionContext): MiniAppSession {
            sessionCreateCount += 1
            error("Session creation is outside metadata access")
        }
    }
}
