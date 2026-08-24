package ge.yet.game.miniapp.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement

fun interface MiniAppSnapshotMigration {
    fun migrate(payload: JsonElement): JsonElement
}

class MiniAppSnapshotSpec<T>(
    val serializer: KSerializer<T>,
    val currentVersion: Int,
    migrations: Map<Int, MiniAppSnapshotMigration> = emptyMap(),
) {
    val migrations: Map<Int, MiniAppSnapshotMigration> = migrations.toMap()

    init {
        require(currentVersion > 0) { "Mini-app snapshot version must be positive" }
        require(this.migrations.keys.all { it in 1 until currentVersion }) {
            "Mini-app snapshot migration versions must be positive and lower than currentVersion"
        }
    }
}
