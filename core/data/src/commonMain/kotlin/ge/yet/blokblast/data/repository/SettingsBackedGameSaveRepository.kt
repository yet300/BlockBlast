package ge.yet.blokblast.data.repository

import com.app.common.AppDispatchers
import com.russhwolf.settings.Settings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.domain.model.GameState
import ge.yet.blokblast.domain.repository.GameSaveRepository
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
            cached = state
            val envelope = SavedGame(version = CURRENT_SAVE_VERSION, state = state)
            withContext(dispatchers.io) {
                settings.putString(KEY_SAVE, json.encodeToString(SavedGame.serializer(), envelope))
            }
        }
    }

    override suspend fun load(): GameState? = mutex.withLock {
        if (loaded) return@withLock cached
        val raw = withContext(dispatchers.io) { settings.getStringOrNull(KEY_SAVE) }
        val parsed = raw?.let {
            runCatching { json.decodeFromString(SavedGame.serializer(), it) }.getOrNull()
        }
        cached = parsed?.takeIf { it.version == CURRENT_SAVE_VERSION }?.state
        // Drop unreadable / wrong-version blobs so we don't pay the parse cost
        // every cold start and don't keep a one-way trap for users.
        if (raw != null && cached == null) {
            withContext(dispatchers.io) { settings.remove(KEY_SAVE) }
        }
        loaded = true
        cached
    }

    override suspend fun clear() {
        mutex.withLock {
            cached = null
            loaded = true
            withContext(dispatchers.io) { settings.remove(KEY_SAVE) }
        }
    }

    private companion object {
        const val KEY_SAVE = "blockblast.game_save"
        val json = Json { ignoreUnknownKeys = true }
    }
}
