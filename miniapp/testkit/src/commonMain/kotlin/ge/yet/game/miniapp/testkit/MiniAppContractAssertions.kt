package ge.yet.game.miniapp.testkit

import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.requireValid
import ge.yet.game.miniapp.compose.MiniAppManifest
import ge.yet.game.miniapp.compose.MiniAppPlugin
import ge.yet.game.miniapp.compose.MiniAppRegistry
import ge.yet.game.miniapp.compose.MiniAppSession
import ge.yet.game.miniapp.metro.RetainedMiniAppSession
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

object MiniAppContractAssertions {
    fun assertSinglePlugin(registry: MiniAppRegistry, expectedId: MiniAppId) {
        assertEquals(listOf(expectedId), registry.manifests.map { it.id })
        assertNotNull(registry[expectedId])
    }

    fun assertManifest(plugin: MiniAppPlugin, expectedId: MiniAppId) {
        assertEquals(expectedId, plugin.manifest.id)
        plugin.manifest.id.requireValid()
    }

    fun assertRetainedGraphSession(session: MiniAppSession) {
        assertTrue(
            session is RetainedMiniAppSession<*>,
            "MiniAppPlugin must return RetainedMiniAppSession so the child graph stays alive",
        )
    }

    fun assertBackConsumed(session: MiniAppSession) {
        assertTrue(session.handleBack())
    }

    fun assertBackNotConsumed(session: MiniAppSession) {
        assertFalse(session.handleBack())
    }

    suspend fun assertResourcesResolvable(manifest: MiniAppManifest) {
        assertTrue(getString(manifest.title).isNotBlank())
        assertTrue(getString(manifest.description).isNotBlank())
        val environment = getSystemResourceEnvironment()
        assertTrue(getDrawableResourceBytes(environment, manifest.icon).isNotEmpty())
        manifest.cover?.let { cover ->
            assertTrue(getDrawableResourceBytes(environment, cover).isNotEmpty())
        }
    }
}
