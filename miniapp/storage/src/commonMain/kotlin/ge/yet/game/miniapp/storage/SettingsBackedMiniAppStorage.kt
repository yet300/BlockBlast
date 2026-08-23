package ge.yet.game.miniapp.storage

import com.app.common.AppDispatchers
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.FlowSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppSnapshotSpec
import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.api.requireValid
import ge.yet.game.miniapp.api.storageKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSettingsApi::class)
internal class SettingsBackedMiniAppStorage(
    private val miniAppId: MiniAppId,
    settings: ObservableSettings,
    dispatchers: AppDispatchers,
    legacyPhysicalKeys: Map<String, String> = emptyMap(),
) : MiniAppStorage {
    private val settings: FlowSettings = settings.toFlowSettings(dispatchers.io)
    private val legacyPhysicalKeys = legacyPhysicalKeys.toMap()
    private val snapshotMutex = Mutex()

    init {
        miniAppId.requireValid()
        this.legacyPhysicalKeys.keys.forEach(miniAppId::storageKey)
    }

    override suspend fun getBoolean(localName: String, defaultValue: Boolean): Boolean =
        settings.getBoolean(key(localName), defaultValue)

    override suspend fun putBoolean(localName: String, value: Boolean) =
        settings.putBoolean(key(localName), value)

    override fun observeBoolean(localName: String, defaultValue: Boolean): Flow<Boolean> =
        settings.getBooleanFlow(key(localName), defaultValue)

    override suspend fun getInt(localName: String, defaultValue: Int): Int =
        settings.getInt(key(localName), defaultValue)

    override suspend fun putInt(localName: String, value: Int) =
        settings.putInt(key(localName), value)

    override fun observeInt(localName: String, defaultValue: Int): Flow<Int> =
        settings.getIntFlow(key(localName), defaultValue)

    override suspend fun getLong(localName: String, defaultValue: Long): Long =
        settings.getLong(key(localName), defaultValue)

    override suspend fun putLong(localName: String, value: Long) =
        settings.putLong(key(localName), value)

    override fun observeLong(localName: String, defaultValue: Long): Flow<Long> =
        settings.getLongFlow(key(localName), defaultValue)

    override suspend fun getFloat(localName: String, defaultValue: Float): Float =
        settings.getFloat(key(localName), defaultValue)

    override suspend fun putFloat(localName: String, value: Float) =
        settings.putFloat(key(localName), value)

    override fun observeFloat(localName: String, defaultValue: Float): Flow<Float> =
        settings.getFloatFlow(key(localName), defaultValue)

    override suspend fun getDouble(localName: String, defaultValue: Double): Double =
        settings.getDouble(key(localName), defaultValue)

    override suspend fun putDouble(localName: String, value: Double) =
        settings.putDouble(key(localName), value)

    override fun observeDouble(localName: String, defaultValue: Double): Flow<Double> =
        settings.getDoubleFlow(key(localName), defaultValue)

    override suspend fun getString(localName: String, defaultValue: String): String =
        settings.getString(key(localName), defaultValue)

    override suspend fun putString(localName: String, value: String) =
        settings.putString(key(localName), value)

    override fun observeString(localName: String, defaultValue: String): Flow<String> =
        settings.getStringFlow(key(localName), defaultValue)

    override suspend fun remove(localName: String) {
        settings.remove(key(localName))
    }

    override suspend fun <T> readSnapshot(
        localName: String,
        spec: MiniAppSnapshotSpec<T>,
    ): T? = snapshotMutex.withLock {
        val physicalKey = key(localName)
        val raw = settings.getStringOrNull(physicalKey) ?: return@withLock null
        val decodedResult = runCatching {
            val stored = json.decodeFromString(StoredMiniAppSnapshot.serializer(), raw)
            require(stored.version in 1..spec.currentVersion)

            var version = stored.version
            var payload = stored.payload
            while (version < spec.currentVersion) {
                val migration = requireNotNull(spec.migrations[version])
                payload = migration.migrate(payload)
                version += 1
            }

            val value = json.decodeFromJsonElement(spec.serializer, payload)
            SnapshotRead(value, migratedPayload = payload.takeIf { stored.version != version })
        }
        if (decodedResult.isFailure) {
            settings.remove(physicalKey)
            return@withLock null
        }
        val decoded = decodedResult.getOrThrow()

        decoded.migratedPayload?.let { payload ->
            settings.putString(
                physicalKey,
                json.encodeToString(
                    StoredMiniAppSnapshot.serializer(),
                    StoredMiniAppSnapshot(spec.currentVersion, payload),
                ),
            )
        }
        decoded.value
    }

    override suspend fun <T> writeSnapshot(
        localName: String,
        value: T,
        spec: MiniAppSnapshotSpec<T>,
    ) {
        val encoded = json.encodeToString(
            StoredMiniAppSnapshot.serializer(),
            StoredMiniAppSnapshot(
                version = spec.currentVersion,
                payload = json.encodeToJsonElement(spec.serializer, value),
            ),
        )
        snapshotMutex.withLock {
            settings.putString(key(localName), encoded)
        }
    }

    private fun key(localName: String): String =
        legacyPhysicalKeys[localName] ?: miniAppId.storageKey(localName).value

    private data class SnapshotRead<T>(
        val value: T,
        val migratedPayload: kotlinx.serialization.json.JsonElement?,
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
