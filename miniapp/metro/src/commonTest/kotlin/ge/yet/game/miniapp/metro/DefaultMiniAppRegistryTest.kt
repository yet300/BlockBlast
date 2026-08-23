package ge.yet.game.miniapp.metro

import com.arkivanov.decompose.ComponentContext
import ge.yet.game.miniapp.api.MiniAppCategoryId
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.compose.MiniAppManifest
import ge.yet.game.miniapp.compose.MiniAppPlugin
import ge.yet.game.miniapp.compose.MiniAppSession
import ge.yet.game.miniapp.compose.MiniAppSessionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultMiniAppRegistryTest {

    @Test
    fun malformed_id_fails_during_registry_creation() {
        val error = assertFailsWith<IllegalArgumentException> {
            DefaultMiniAppRegistry(setOf(fakePlugin(id = "Invalid")), emptySet())
        }

        assertTrue(error.message.orEmpty().contains("Malformed mini-app ids: [Invalid]"))
    }

    @Test
    fun duplicate_ids_fail_before_indexing() {
        val error = assertFailsWith<IllegalArgumentException> {
            DefaultMiniAppRegistry(
                setOf(fakePlugin(id = "game.one"), fakePlugin(id = "game.one")),
                emptySet(),
            )
        }

        assertTrue(error.message.orEmpty().contains("Duplicate mini-app ids: [game.one]"))
    }

    @Test
    fun manifests_are_sorted_by_priority_then_id() {
        val registry = DefaultMiniAppRegistry(
            setOf(
                fakePlugin(id = "game.gamma", sortPriority = 2),
                fakePlugin(id = "game.beta", sortPriority = 1),
                fakePlugin(id = "game.alpha", sortPriority = 1),
            ),
            emptySet(),
        )

        assertEquals(
            listOf("game.alpha", "game.beta", "game.gamma"),
            registry.manifests.map { it.id.value },
        )
    }

    @Test
    fun exact_id_resolves_original_plugin() {
        val plugin = fakePlugin(id = "game.one")
        val registry = DefaultMiniAppRegistry(setOf(plugin), emptySet())

        assertTrue(registry[MiniAppId("game.one")] === plugin)
    }

    @Test
    fun missing_id_returns_null() {
        val registry = DefaultMiniAppRegistry(setOf(fakePlugin(id = "game.one")), emptySet())

        assertNull(registry[MiniAppId("game.missing")])
    }

    @Test
    fun registry_snapshots_mutable_input() {
        val plugins = mutableSetOf(fakePlugin(id = "game.one"))
        val registry = DefaultMiniAppRegistry(plugins, emptySet())
        plugins += fakePlugin(id = "game.two")

        assertEquals(listOf("game.one"), registry.manifests.map { it.id.value })
        assertNull(registry[MiniAppId("game.two")])
    }

    @Test
    fun manifest_is_read_once_during_assembly() {
        var manifestReadCount = 0
        val plugin = fakePlugin(id = "game.one") { manifestReadCount += 1 }

        val registry = DefaultMiniAppRegistry(setOf(plugin), emptySet())
        registry.manifests
        registry[MiniAppId("game.one")]

        assertEquals(1, manifestReadCount)
    }

    @Test
    fun empty_plugin_set_produces_empty_registry() {
        val registry = DefaultMiniAppRegistry(emptySet(), emptySet())

        assertTrue(registry.manifests.isEmpty())
    }

    @Test
    fun one_expectation_requires_exact_actual_ids() {
        val error = assertFailsWith<IllegalArgumentException> {
            DefaultMiniAppRegistry(
                setOf(fakePlugin(id = "sample.counter")),
                setOf(expectation("game.blockblast")),
            )
        }

        assertTrue(error.message.orEmpty().contains("missing=[game.blockblast]"))
        assertTrue(error.message.orEmpty().contains("unexpected=[sample.counter]"))
    }

    @Test
    fun expectation_ids_are_snapshotted_once_before_mismatch_evaluation() {
        var expectedIdsReadCount = 0
        val expectation = object : MiniAppRegistryExpectation {
            override val expectedIds: Set<MiniAppId>
                get() {
                    expectedIdsReadCount += 1
                    return if (expectedIdsReadCount == 1) {
                        emptySet()
                    } else {
                        setOf(MiniAppId("game.one"))
                    }
                }
        }

        val error = assertFailsWith<IllegalArgumentException> {
            DefaultMiniAppRegistry(setOf(fakePlugin(id = "game.one")), setOf(expectation))
        }

        assertTrue(error.message.orEmpty().contains("unexpected=[game.one]"))
        assertEquals(1, expectedIdsReadCount)
    }

    @Test
    fun expectations_are_snapshotted_before_cardinality_and_selection() {
        val error = assertFailsWith<IllegalArgumentException> {
            DefaultMiniAppRegistry(
                setOf(fakePlugin(id = "game.one")),
                ClearingOnSizeSet(setOf(expectation("game.two"))),
            )
        }

        assertTrue(error.message.orEmpty().contains("missing=[game.two]"))
        assertTrue(error.message.orEmpty().contains("unexpected=[game.one]"))
    }

    @Test
    fun multi_id_expectation_diagnostics_are_sorted() {
        val error = assertFailsWith<IllegalArgumentException> {
            DefaultMiniAppRegistry(
                setOf(fakePlugin(id = "sample.zeta"), fakePlugin(id = "sample.alpha")),
                setOf(expectation("game.zeta", "game.alpha")),
            )
        }

        assertTrue(error.message.orEmpty().contains("missing=[game.alpha, game.zeta]"))
        assertTrue(error.message.orEmpty().contains("unexpected=[sample.alpha, sample.zeta]"))
    }

    @Test
    fun multiple_expectations_fail_deterministically() {
        val error = assertFailsWith<IllegalArgumentException> {
            DefaultMiniAppRegistry(
                emptySet(),
                setOf(expectation("game.one"), expectation("game.two")),
            )
        }

        assertEquals("Expected at most one production mini-app expectation", error.message)
    }

    @Test
    fun returned_multi_element_manifest_list_cannot_change_later_reads() {
        val registry = DefaultMiniAppRegistry(
            setOf(fakePlugin(id = "game.one"), fakePlugin(id = "game.two")),
            emptySet(),
        )
        val returned = registry.manifests

        (returned as? MutableList<MiniAppManifest>)?.clear()

        assertEquals(listOf("game.one", "game.two"), registry.manifests.map { it.id.value })
    }

    private fun expectation(vararg ids: String): MiniAppRegistryExpectation =
        object : MiniAppRegistryExpectation {
            override val expectedIds: Set<MiniAppId> = ids.map(::MiniAppId).toSet()
        }

    private fun fakePlugin(
        id: String,
        sortPriority: Int = 0,
        onManifestRead: () -> Unit = {},
    ): MiniAppPlugin =
        object : MiniAppPlugin {
            override val manifest: MiniAppManifest
                get() {
                    onManifestRead()
                    return MiniAppManifest(
                        id = MiniAppId(id),
                        title = fakeTitle,
                        description = fakeDescription,
                        icon = fakeIcon,
                        cover = null,
                        category = MiniAppCategoryId("game"),
                        sortPriority = sortPriority,
                    )
                }

            override fun createSession(context: MiniAppSessionContext): MiniAppSession =
                error("Session creation is outside registry assembly")
        }

    private class ClearingOnSizeSet<T>(
        private var elements: Set<T>,
    ) : Set<T> {
        override val size: Int
            get() {
                elements = emptySet()
                return elements.size
            }

        override fun contains(element: T): Boolean = elements.contains(element)

        override fun containsAll(elements: Collection<T>): Boolean = this.elements.containsAll(elements)

        override fun isEmpty(): Boolean = elements.isEmpty()

        override fun iterator(): Iterator<T> = elements.iterator()
    }
}
