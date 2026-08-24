package ge.yet.game.miniapp.api

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MiniAppStorageContractTest {

    @Test
    fun snapshot_specs_require_a_positive_current_version() {
        assertFailsWith<IllegalArgumentException> {
            MiniAppSnapshotSpec(
                serializer = String.serializer(),
                currentVersion = 0,
            )
        }
    }

    @Test
    fun snapshot_migrations_require_positive_source_versions_before_current() {
        listOf(0, 2).forEach { sourceVersion ->
            assertFailsWith<IllegalArgumentException> {
                MiniAppSnapshotSpec(
                    serializer = String.serializer(),
                    currentVersion = 2,
                    migrations = mapOf(
                        sourceVersion to MiniAppSnapshotMigration { JsonPrimitive("migrated") },
                    ),
                )
            }
        }
    }

    @Test
    fun partial_reset_failure_snapshots_stable_failed_ids() {
        val mutableIds = mutableSetOf(MiniAppId("game.snake"))

        val result = MiniAppDataResetResult.PartialFailure(mutableIds)
        mutableIds += MiniAppId("game.blocks")

        assertEquals(setOf(MiniAppId("game.snake")), result.failedMiniAppIds)
    }

    @Test
    fun partial_reset_failure_rejects_an_empty_id_set() {
        assertFailsWith<IllegalArgumentException> {
            MiniAppDataResetResult.PartialFailure(emptySet())
        }
    }

    @Test
    fun legacy_storage_mapping_snapshots_valid_local_names_and_physical_keys() {
        val mutableMappings = mutableMapOf("best_score" to "blockblast.best_score")

        val declaration = MiniAppLegacyStorageKeys(
            miniAppId = MiniAppId("game.blockblast"),
            localToPhysicalKeys = mutableMappings,
        )
        mutableMappings["save"] = "blockblast.game_save"

        assertEquals(
            mapOf("best_score" to "blockblast.best_score"),
            declaration.localToPhysicalKeys,
        )
    }

    @Test
    fun legacy_storage_mapping_rejects_invalid_entries() {
        assertFailsWith<IllegalArgumentException> {
            MiniAppLegacyStorageKeys(
                miniAppId = MiniAppId("game.blockblast"),
                localToPhysicalKeys = mapOf("BestScore" to "blockblast.best_score"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MiniAppLegacyStorageKeys(
                miniAppId = MiniAppId("game.blockblast"),
                localToPhysicalKeys = mapOf("best_score" to ""),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MiniAppLegacyStorageKeys(
                miniAppId = MiniAppId("game.blockblast"),
                localToPhysicalKeys = mapOf("best_score" to "miniapp.game.snake.best_score"),
            )
        }
    }
}
