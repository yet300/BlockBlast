package ge.yet.game.miniapp.metro

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.isValid
import ge.yet.game.miniapp.compose.MiniAppManifest
import ge.yet.game.miniapp.compose.MiniAppPlugin
import ge.yet.game.miniapp.compose.MiniAppRegistry

@Inject
@SingleIn(AppScope::class)
class DefaultMiniAppRegistry(
    plugins: Set<MiniAppPlugin>,
    expectations: Set<MiniAppRegistryExpectation>,
) : MiniAppRegistry {
    private data class Entry(
        val plugin: MiniAppPlugin,
        val manifest: MiniAppManifest,
    )

    private val byId: Map<MiniAppId, Entry>
    private val manifestSnapshot: List<MiniAppManifest>

    override val manifests: List<MiniAppManifest>
        get() = manifestSnapshot.toList()

    init {
        val pluginSnapshot = plugins.toSet()
        val expectationSnapshot = mutableSetOf<MiniAppRegistryExpectation>().apply {
            expectations.forEach(::add)
        }
        val entries = pluginSnapshot.map { Entry(it, it.manifest) }
        val malformed = entries.map { it.manifest.id }
            .filterNot(MiniAppId::isValid)
            .sortedBy(MiniAppId::value)
        require(malformed.isEmpty()) {
            "Malformed mini-app ids: ${malformed.map { it.value }}"
        }

        val duplicates = entries.groupBy { it.manifest.id }
            .filterValues { it.size > 1 }
            .keys
            .sortedBy(MiniAppId::value)
        require(duplicates.isEmpty()) {
            "Duplicate mini-app ids: ${duplicates.map { it.value }}"
        }

        require(expectationSnapshot.size <= 1) {
            "Expected at most one production mini-app expectation"
        }

        byId = entries.associateBy { it.manifest.id }.toMap()
        manifestSnapshot = entries.map(Entry::manifest)
            .sortedWith(compareBy<MiniAppManifest> { it.sortPriority }.thenBy { it.id.value })
            .toList()

        expectationSnapshot.singleOrNull()?.let { expectation ->
            val expectedIds = expectation.expectedIds.toSet()
            val actual = byId.keys
            val missing = (expectedIds - actual).sortedBy(MiniAppId::value)
            val unexpected = (actual - expectedIds).sortedBy(MiniAppId::value)
            require(missing.isEmpty() && unexpected.isEmpty()) {
                "Production mini-app registry mismatch: missing=${missing.map { it.value }}, " +
                    "unexpected=${unexpected.map { it.value }}"
            }
        }
    }

    override fun get(id: MiniAppId): MiniAppPlugin? = byId[id]?.plugin
}
