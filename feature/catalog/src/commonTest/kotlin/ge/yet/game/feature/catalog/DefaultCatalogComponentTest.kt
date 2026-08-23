package ge.yet.game.feature.catalog

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import ge.yet.game.miniapp.api.MiniAppCategoryId
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.compose.MiniAppManifest
import ge.yet.game.miniapp.compose.MiniAppPlugin
import ge.yet.game.miniapp.compose.MiniAppRegistry
import ge.yet.game.miniapp.compose.MiniAppSession
import ge.yet.game.miniapp.compose.MiniAppSessionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.StringResource

@OptIn(InternalResourceApi::class)
class DefaultCatalogComponentTest {

    @Test
    fun model_exposes_registry_order_without_creating_sessions() {
        val alpha = manifest("game.alpha")
        val beta = manifest("game.beta")
        val plugin = RecordingPlugin(alpha)
        val registry = FakeRegistry(
            manifests = listOf(beta, alpha),
            plugins = mapOf(alpha.id to plugin),
        )

        val component = DefaultCatalogComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            registry = registry,
            onPlay = {},
        )

        assertEquals(registry.manifests, component.model.value.manifests)
        assertEquals(listOf(beta, alpha), component.model.value.manifests)
        assertEquals(0, registry.pluginLookupCount)
        assertEquals(0, plugin.sessionCreateCount)
    }

    @Test
    fun play_forwards_the_exact_manifest_id_once_per_click() {
        val id = MiniAppId("game.beta")
        val received = mutableListOf<MiniAppId>()
        val component = DefaultCatalogComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            registry = FakeRegistry(listOf(manifest(id.value))),
            onPlay = received::add,
        )

        component.onPlayClicked(id)

        assertEquals(listOf(id), received)

        component.onPlayClicked(id)

        assertEquals(listOf(id, id), received)
    }

    @Test
    fun empty_registry_produces_an_empty_catalog_model() {
        val component = DefaultCatalogComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            registry = FakeRegistry(emptyList()),
            onPlay = {},
        )

        assertEquals(emptyList(), component.model.value.manifests)
    }

    @Test
    fun model_snapshots_registry_manifests_at_construction() {
        val alpha = manifest("game.alpha")
        val beta = manifest("game.beta")
        val exposedManifests = mutableListOf(alpha)
        val registry = MutableBackedRegistry(exposedManifests)
        val component = DefaultCatalogComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            registry = registry,
            onPlay = {},
        )

        exposedManifests.clear()
        exposedManifests += beta

        assertEquals(listOf(beta), registry.manifests)
        assertEquals(listOf(alpha), component.model.value.manifests)
    }

    private fun manifest(id: String): MiniAppManifest =
        MiniAppManifest(
            id = MiniAppId(id),
            title = StringResource("test:$id:title", "title", emptySet()),
            description = StringResource("test:$id:description", "description", emptySet()),
            icon = DrawableResource("test:$id:icon", emptySet()),
            cover = null,
            category = MiniAppCategoryId("game"),
            sortPriority = 0,
        )

    private class FakeRegistry(
        manifests: List<MiniAppManifest>,
        plugins: Map<MiniAppId, MiniAppPlugin> = emptyMap(),
    ) : MiniAppRegistry {
        override val manifests: List<MiniAppManifest> = manifests.toList()
        private val plugins: Map<MiniAppId, MiniAppPlugin> = plugins.toMap()
        var pluginLookupCount: Int = 0
            private set

        override fun get(id: MiniAppId): MiniAppPlugin? {
            pluginLookupCount += 1
            return plugins[id]
        }
    }

    private class MutableBackedRegistry(
        override val manifests: List<MiniAppManifest>,
    ) : MiniAppRegistry {
        override fun get(id: MiniAppId): MiniAppPlugin? = null
    }

    private class RecordingPlugin(
        override val manifest: MiniAppManifest,
    ) : MiniAppPlugin {
        var sessionCreateCount: Int = 0
            private set

        override fun createSession(context: MiniAppSessionContext): MiniAppSession {
            sessionCreateCount += 1
            error("Catalog must not create mini-app sessions")
        }
    }
}
