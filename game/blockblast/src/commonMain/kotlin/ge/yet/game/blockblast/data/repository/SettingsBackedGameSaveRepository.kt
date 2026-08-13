package ge.yet.game.blockblast.data.repository

import com.app.common.AppDispatchers
import com.russhwolf.settings.Settings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.blockblast.domain.model.Grid
import ge.yet.game.blockblast.domain.repository.GameSaveRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
 * Disk-backed save store: serializes [GameState] as JSON into the shared
 * multiplatform-settings store. Survives process death.
 *
 * A process-local [cached] copy avoids re-parsing JSON on every [load] and
 * keeps reads off the IO dispatcher once warm. [mutex] serializes writes so
 * concurrent saves from the engine's debounced autosave don't interleave.
 */
@SingleIn(AppScope::class)
@Inject
internal class SettingsBackedGameSaveRepository(
    private val settings: Settings,
    private val dispatchers: AppDispatchers,
) : GameSaveRepository {

    private val mutex = Mutex()
    private var cached: GameState? = null
    private var loaded = false

    override suspend fun save(state: GameState) {
        mutex.withLock {
            val snapshot = state.deepCopy()
            val envelope = SavedGame(version = CURRENT_SAVE_VERSION, state = snapshot)
            withContext(dispatchers.io) {
                settings.putString(KEY_SAVE, json.encodeToString(SavedGame.serializer(), envelope))
                cached = snapshot
                loaded = true
            }
        }
    }

    override suspend fun load(): GameState? = mutex.withLock {
        if (loaded) return@withLock cached?.deepCopy()
        val raw = withContext(dispatchers.io) { settings.getStringOrNull(KEY_SAVE) }
        val parsed = raw?.let {
            runCatching { json.decodeFromString(SavedGame.serializer(), it) }.getOrNull()
        }
        cached = parsed
            ?.takeIf { it.version == CURRENT_SAVE_VERSION }
            ?.state
            ?.deepCopy()
        // Drop unreadable / wrong-version blobs so we don't pay the parse cost
        // every cold start and don't keep a one-way trap for users.
        if (raw != null && cached == null) {
            withContext(dispatchers.io) { settings.remove(KEY_SAVE) }
        }
        loaded = true
        cached?.deepCopy()
    }

    override suspend fun clear() {
        mutex.withLock {
            withContext(dispatchers.io) {
                settings.remove(KEY_SAVE)
                cached = null
                loaded = true
            }
        }
    }

    private companion object {
        const val KEY_SAVE = "blockblast.game_save"
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
