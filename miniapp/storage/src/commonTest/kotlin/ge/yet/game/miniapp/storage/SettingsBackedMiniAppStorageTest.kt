package ge.yet.game.miniapp.storage

import com.app.common.AppDispatchers
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.observable.makeObservable
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppLegacyStorageKeys
import ge.yet.game.miniapp.api.MiniAppSnapshotMigration
import ge.yet.game.miniapp.api.MiniAppSnapshotSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSettingsApi::class)
class SettingsBackedMiniAppStorageTest {

    @Test
    fun two_storages_cannot_read_each_others_local_key() = runTest {
        val settings = MapSettings()
        val snake = storage(settings, MiniAppId("game.snake"))
        val blocks = storage(settings, MiniAppId("game.blocks"))

        snake.putLong("best_score", 42)

        assertEquals(42, snake.getLong("best_score"))
        assertEquals(0, blocks.getLong("best_score"))
    }

    @Test
    fun primitive_values_round_trip_through_the_namespace() = runTest {
        val storage = storage(MapSettings(), MiniAppId("game.snake"))

        storage.putBoolean("seen", true)
        storage.putInt("level", 3)
        storage.putLong("score", 42L)
        storage.putFloat("speed", 1.5f)
        storage.putDouble("ratio", 2.25)
        storage.putString("mode", "daily")

        assertEquals(true, storage.getBoolean("seen"))
        assertEquals(3, storage.getInt("level"))
        assertEquals(42L, storage.getLong("score"))
        assertEquals(1.5f, storage.getFloat("speed"))
        assertEquals(2.25, storage.getDouble("ratio"))
        assertEquals("daily", storage.getString("mode"))
    }

    @Test
    fun invalid_local_names_are_rejected_before_io() = runTest {
        val settings = MapSettings()
        val storage = storage(settings, MiniAppId("game.snake"))

        assertFailsWith<IllegalArgumentException> { storage.putLong("best-score", 1) }
        assertEquals(emptySet(), settings.keys)
    }

    @Test
    fun observer_emits_default_after_value_is_removed() = runTest {
        val storage = storage(MapSettings(), MiniAppId("game.snake"))
        val values = mutableListOf<Long>()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            storage.observeLong("score", defaultValue = 0L).take(3).toList(values)
        }

        storage.putLong("score", 7L)
        storage.remove("score")
        collection.join()

        assertEquals(listOf(0L, 7L, 0L), values)
    }

    @Test
    fun legacy_mapping_keeps_the_existing_physical_key() = runTest {
        val settings = MapSettings("blockblast.best_score" to 9L)
        val storage = storage(
            settings = settings,
            id = MiniAppId("game.blockblast"),
            legacyPhysicalKeys = mapOf("best_score" to "blockblast.best_score"),
        )

        assertEquals(9L, storage.getLong("best_score"))
        storage.putLong("best_score", 12L)

        assertEquals(12L, settings.getLong("blockblast.best_score", 0L))
        assertEquals(false, settings.hasKey("miniapp.game.blockblast.best_score"))
    }

    @Test
    fun snapshot_round_trip_uses_the_current_version() = runTest {
        val storage = storage(MapSettings(), MiniAppId("game.snake"))
        val spec = MiniAppSnapshotSpec(Snapshot.serializer(), currentVersion = 2)

        storage.writeSnapshot("save", Snapshot(score = 11), spec)

        assertEquals(Snapshot(score = 11), storage.readSnapshot("save", spec))
    }

    @Test
    fun snapshot_migrations_are_applied_in_version_order() = runTest {
        val settings = MapSettings(
            "miniapp.game.snake.save" to
                """{"version":1,"payload":{"score":4}}""",
        )
        val storage = storage(settings, MiniAppId("game.snake"))
        val spec = MiniAppSnapshotSpec(
            serializer = Snapshot.serializer(),
            currentVersion = 3,
            migrations = mapOf(
                1 to MiniAppSnapshotMigration { payload ->
                    JsonObject(payload.jsonObject + ("score" to JsonPrimitive(5)))
                },
                2 to MiniAppSnapshotMigration { payload ->
                    JsonObject(payload.jsonObject + ("score" to JsonPrimitive(6)))
                },
            ),
        )

        assertEquals(Snapshot(score = 6), storage.readSnapshot("save", spec))
    }

    @Test
    fun missing_snapshot_migration_removes_the_unreadable_value() = runTest {
        val settings = MapSettings(
            "miniapp.game.snake.save" to
                """{"version":1,"payload":{"score":4}}""",
        )
        val storage = storage(settings, MiniAppId("game.snake"))

        assertNull(
            storage.readSnapshot(
                "save",
                MiniAppSnapshotSpec(Snapshot.serializer(), currentVersion = 2),
            ),
        )
        assertEquals(false, settings.hasKey("miniapp.game.snake.save"))
    }

    @Test
    fun corrupt_snapshot_is_removed_after_the_first_read() = runTest {
        val settings = MapSettings("miniapp.game.snake.save" to "not-json")
        val storage = storage(settings, MiniAppId("game.snake"))

        assertNull(
            storage.readSnapshot(
                "save",
                MiniAppSnapshotSpec(Snapshot.serializer(), currentVersion = 1),
            ),
        )
        assertEquals(false, settings.hasKey("miniapp.game.snake.save"))
    }

    @Test
    fun provider_returns_one_storage_per_validated_id() {
        val provider = DefaultMiniAppStorageProvider(
            settings = MapSettings().makeObservable(),
            dispatchers = testDispatchers(),
            legacyStorageKeys = setOf(
                MiniAppLegacyStorageKeys(
                    miniAppId = MiniAppId("game.blockblast"),
                    localToPhysicalKeys = mapOf("best_score" to "blockblast.best_score"),
                ),
            ),
        )

        assertSame(
            provider.storageFor(MiniAppId("game.blockblast")),
            provider.storageFor(MiniAppId("game.blockblast")),
        )
        assertFailsWith<IllegalArgumentException> {
            provider.storageFor(MiniAppId("Game.BlockBlast"))
        }
    }

    @Test
    fun provider_rejects_duplicate_legacy_local_names() {
        assertFailsWith<IllegalArgumentException> {
            DefaultMiniAppStorageProvider(
                settings = MapSettings().makeObservable(),
                dispatchers = testDispatchers(),
                legacyStorageKeys = setOf(
                    MiniAppLegacyStorageKeys(
                        miniAppId = MiniAppId("game.blockblast"),
                        localToPhysicalKeys = mapOf("best_score" to "first.best"),
                    ),
                    MiniAppLegacyStorageKeys(
                        miniAppId = MiniAppId("game.blockblast"),
                        localToPhysicalKeys = mapOf("best_score" to "second.best"),
                    ),
                ),
            )
        }
    }

    @Test
    fun provider_rejects_a_legacy_physical_key_shared_by_two_mini_apps() {
        assertFailsWith<IllegalArgumentException> {
            DefaultMiniAppStorageProvider(
                settings = MapSettings().makeObservable(),
                dispatchers = testDispatchers(),
                legacyStorageKeys = setOf(
                    MiniAppLegacyStorageKeys(
                        miniAppId = MiniAppId("game.blocks"),
                        localToPhysicalKeys = mapOf("best_score" to "legacy.shared_score"),
                    ),
                    MiniAppLegacyStorageKeys(
                        miniAppId = MiniAppId("game.snake"),
                        localToPhysicalKeys = mapOf("best_score" to "legacy.shared_score"),
                    ),
                ),
            )
        }
    }

    private fun storage(
        settings: Settings,
        id: MiniAppId,
        legacyPhysicalKeys: Map<String, String> = emptyMap(),
    ): SettingsBackedMiniAppStorage = SettingsBackedMiniAppStorage(
        miniAppId = id,
        settings = settings.makeObservable(),
        dispatchers = testDispatchers(),
        legacyPhysicalKeys = legacyPhysicalKeys,
    )

    private fun testDispatchers(): AppDispatchers = AppDispatchers(
        default = Dispatchers.Unconfined,
        io = Dispatchers.Unconfined,
    )

    @Serializable
    private data class Snapshot(val score: Int)
}
