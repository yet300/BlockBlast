package ge.yet.game.miniapp.storage

import com.app.common.AppDispatchers
import com.russhwolf.settings.ObservableSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppLegacyStorageKeys
import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.api.MiniAppStorageProvider
import ge.yet.game.miniapp.api.requireValid
import kotlinx.coroutines.flow.MutableStateFlow

@Inject
@SingleIn(AppScope::class)
internal class DefaultMiniAppStorageProvider(
    private val settings: ObservableSettings,
    private val dispatchers: AppDispatchers,
    legacyStorageKeys: Set<MiniAppLegacyStorageKeys>,
) : MiniAppStorageProvider {
    private val legacyPhysicalKeysById = snapshotLegacyMappings(legacyStorageKeys)
    private val storages = MutableStateFlow<Map<MiniAppId, MiniAppStorage>>(emptyMap())

    override fun storageFor(id: MiniAppId): MiniAppStorage {
        id.requireValid()
        while (true) {
            val current = storages.value
            current[id]?.let { return it }
            val created = SettingsBackedMiniAppStorage(
                miniAppId = id,
                settings = settings,
                dispatchers = dispatchers,
                legacyPhysicalKeys = legacyPhysicalKeysById[id].orEmpty(),
            )
            if (storages.compareAndSet(current, current + (id to created))) return created
        }
    }

    private fun snapshotLegacyMappings(
        declarations: Set<MiniAppLegacyStorageKeys>,
    ): Map<MiniAppId, Map<String, String>> {
        val result = mutableMapOf<MiniAppId, MutableMap<String, String>>()
        val physicalKeyOwners = mutableMapOf<String, MiniAppId>()
        declarations.forEach { declaration ->
            val mappings = result.getOrPut(declaration.miniAppId) { mutableMapOf() }
            declaration.localToPhysicalKeys.forEach { (localName, physicalKey) ->
                require(mappings.put(localName, physicalKey) == null) {
                    "Duplicate legacy storage mapping for ${declaration.miniAppId.value}:$localName"
                }
                require(physicalKeyOwners.put(physicalKey, declaration.miniAppId) == null) {
                    "Legacy storage key '$physicalKey' is owned by more than one MiniApp"
                }
            }
        }
        return result.mapValues { (_, mappings) -> mappings.toMap() }
    }
}
