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
import kotlinx.serialization.json.Json

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
            withContext(dispatchers.io) {
                settings.putString(KEY_SAVE, json.encodeToString(GameState.serializer(), state))
            }
        }
    }

    override suspend fun load(): GameState? = mutex.withLock {
        if (loaded) return@withLock cached
        val raw = withContext(dispatchers.io) { settings.getStringOrNull(KEY_SAVE) }
        cached = raw?.let {
            runCatching { json.decodeFromString(GameState.serializer(), it) }.getOrNull()
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
