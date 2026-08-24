package ge.yet.game.blockblast.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.blockblast.domain.model.Grid
import ge.yet.game.blockblast.domain.repository.GameSaveRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Versioned save envelope. Bump [CURRENT_SAVE_VERSION] on any incompatible
 * change to [GameState] (or its transitively serialized types). On load we
 * drop saves whose version doesn't match — no migration framework yet.
 */
@Serializable
private data class SavedGame(val version: Int, val state: GameState)

private const val CURRENT_SAVE_VERSION = 1

/**
 * Serializes [GameState] into the legacy-compatible MiniApp storage entry.
 * [mutex] serializes save/load/clear operations inside this repository while
 * avoiding a warm cache that could survive an external all-game-data reset.
 */
@SingleIn(AppScope::class)
@Inject
internal class SettingsBackedGameSaveRepository(
    private val storage: BlockBlastStorage,
) : GameSaveRepository {

    private val mutex = Mutex()

    override suspend fun save(state: GameState) {
        mutex.withLock {
            val snapshot = state.deepCopy()
            val envelope = SavedGame(version = CURRENT_SAVE_VERSION, state = snapshot)
            storage.putString(KEY_SAVE, json.encodeToString(SavedGame.serializer(), envelope))
        }
    }

    override suspend fun load(): GameState? = mutex.withLock {
        val raw = storage.getString(KEY_SAVE)
        val parsed = raw.takeIf(String::isNotEmpty)?.let {
            runCatching { json.decodeFromString(SavedGame.serializer(), it) }.getOrNull()
        }
        val state = parsed
            ?.takeIf { it.version == CURRENT_SAVE_VERSION }
            ?.state
            ?.deepCopy()
        // Drop unreadable / wrong-version blobs so we don't pay the parse cost
        // every cold start and don't keep a one-way trap for users.
        if (raw.isNotEmpty() && state == null) {
            storage.remove(KEY_SAVE)
        }
        state
    }

    override suspend fun clear() {
        mutex.withLock {
            storage.remove(KEY_SAVE)
        }
    }

    private companion object {
        const val KEY_SAVE = "game_save"
        val json = Json { ignoreUnknownKeys = true }
    }
}

private fun GameState.deepCopy(): GameState = copy(
    grid = Grid(grid.cells.copyOf()),
    currentPieces = currentPieces.map { piece ->
        piece.copy(
            shape = piece.shape.copy(cells = piece.shape.cells.toList()),
        )
    },
    lastClearedCells = lastClearedCells.copy(cells = lastClearedCells.cells.toList()),
)
