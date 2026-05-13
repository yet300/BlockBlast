package ge.yet.blokblast.data.repository

import com.app.common.AppDispatchers
import com.russhwolf.settings.MapSettings
import ge.yet.blokblast.domain.model.GameState
import ge.yet.blokblast.domain.model.Grid
import ge.yet.blokblast.domain.model.Piece
import ge.yet.blokblast.domain.model.Polyomino
import ge.yet.blokblast.domain.model.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SettingsBackedGameSaveRepositoryTest {

    private fun newRepo(settings: MapSettings = MapSettings()) =
        SettingsBackedGameSaveRepository(
            settings = settings,
            dispatchers = AppDispatchers(
                default = Dispatchers.Unconfined,
                io = Dispatchers.Unconfined,
            ),
        )

    private val sampleState = GameState(
        grid = Grid().withCell(2, 3, 5),
        score = 1234L,
        bestScore = 5000L,
        comboLevel = 2,
        currentPieces = listOf(
            Piece(
                pieceId = 7L,
                shape = Polyomino(id = "h2", cells = listOf(Position(0, 0), Position(1, 0))),
                colorId = 3,
            ),
        ),
        isGameOver = false,
        revivesUsed = 0,
        bestAtRoundStart = 5000L,
        reviewPromptFiredThisRound = true,
    )

    @Test
    fun load_returns_null_on_empty_store() = runTest {
        val repo = newRepo()
        assertNull(repo.load())
    }

    @Test
    fun save_then_load_round_trip() = runTest {
        val repo = newRepo()
        repo.save(sampleState)
        val loaded = repo.load()
        assertNotNull(loaded)
        assertEquals(sampleState, loaded)
    }

    @Test
    fun load_returns_null_for_corrupt_json() = runTest {
        val settings = MapSettings("blockblast.game_save" to "{not valid json")
        val repo = newRepo(settings)
        assertNull(repo.load())
    }

    @Test
    fun clear_removes_persisted_save() = runTest {
        val repo = newRepo()
        repo.save(sampleState)
        repo.clear()
        assertNull(repo.load())
    }

    @Test
    fun cache_warm_avoids_extra_disk_reads() = runTest {
        val settings = CountingSettings()
        val repo = SettingsBackedGameSaveRepository(
            settings = settings,
            dispatchers = AppDispatchers(
                default = Dispatchers.Unconfined,
                io = Dispatchers.Unconfined,
            ),
        )
        repo.save(sampleState)
        // First load primes `loaded = true` from disk (one read).
        repo.load()
        val readsAfterPrime = settings.readCount
        // Subsequent loads hit the cache.
        repo.load()
        repo.load()
        assertEquals(readsAfterPrime, settings.readCount)
    }

    @Test
    fun load_returns_null_consistently_after_first_miss() = runTest {
        val settings = MapSettings()
        val repo = newRepo(settings)
        assertNull(repo.load())
        // External writes after a miss aren't picked up — cache locked.
        settings.putString(
            "blockblast.game_save",
            """{"score":1}""",
        )
        assertNull(repo.load())
    }

    /** Wraps MapSettings to count getStringOrNull invocations. */
    private class CountingSettings(
        private val delegate: MapSettings = MapSettings(),
    ) : com.russhwolf.settings.Settings by delegate {
        var readCount = 0
        override fun getStringOrNull(key: String): String? {
            readCount += 1
            return delegate.getStringOrNull(key)
        }
    }
}
