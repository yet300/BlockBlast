package ge.yet.blokblast.data.repository

import com.app.common.AppDispatchers
import com.russhwolf.settings.MapSettings
import ge.yet.blokblast.domain.model.GameState
import ge.yet.blokblast.domain.model.Grid
import ge.yet.blokblast.domain.model.Piece
import ge.yet.blokblast.domain.model.Polyomino
import ge.yet.blokblast.domain.model.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SettingsBackedGameSaveRepositoryTest {

    private fun newRepo(settings: com.russhwolf.settings.Settings = MapSettings()) =
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
        val settings = MapSettings()
        val repo = newRepo(settings)
        repo.save(sampleState)
        repo.clear()
        assertNull(repo.load())
        assertNull(newRepo(settings).load())
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
        repo.load()
        val readsAfterPrime = settings.readCount
        // Subsequent loads hit the cache.
        repo.load()
        repo.load()
        assertEquals(0, readsAfterPrime)
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

    @Test
    fun failed_disk_write_does_not_replace_warm_cached_state() = runTest {
        val settings = FailingPutSettings()
        val repo = newRepo(settings)
        val terminalState = sampleState.copy(isGameOver = true)
        repo.save(terminalState)
        assertEquals(terminalState, repo.load())

        settings.failWrites = true
        assertFailsWith<IllegalStateException> {
            repo.save(sampleState.copy(score = 9_999L))
        }

        assertEquals(terminalState, repo.load())
    }

    @Test
    fun cancellation_after_successful_disk_write_keeps_cache_in_sync() = runTest {
        val settings = CancellingPutSettings()
        val repo = newRepo(settings)
        val oldState = sampleState.copy(score = 1_111L)
        repo.save(oldState)
        assertEquals(oldState, repo.load())

        lateinit var saveJob: Job
        settings.afterPut = { saveJob.cancel() }

        val updatedState = sampleState.copy(score = 8_888L)
        saveJob = launch { repo.save(updatedState) }
        saveJob.join()

        assertEquals(true, saveJob.isCancelled)
        assertEquals(updatedState, repo.load())
    }

    @Test
    fun failed_clear_keeps_warm_cached_state() = runTest {
        val settings = FailingRemoveSettings()
        val repo = newRepo(settings)
        repo.save(sampleState)
        assertEquals(sampleState, repo.load())

        settings.failRemoves = true
        assertFailsWith<IllegalStateException> { repo.clear() }

        assertEquals(sampleState, repo.load())
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

    private class FailingPutSettings(
        private val delegate: MapSettings = MapSettings(),
    ) : com.russhwolf.settings.Settings by delegate {
        var failWrites: Boolean = false

        override fun putString(key: String, value: String) {
            if (failWrites) error("disk write failed")
            delegate.putString(key, value)
        }
    }

    private class CancellingPutSettings(
        private val delegate: MapSettings = MapSettings(),
    ) : com.russhwolf.settings.Settings by delegate {
        var afterPut: () -> Unit = {}

        override fun putString(key: String, value: String) {
            delegate.putString(key, value)
            afterPut()
        }
    }

    private class FailingRemoveSettings(
        private val delegate: MapSettings = MapSettings(),
    ) : com.russhwolf.settings.Settings by delegate {
        var failRemoves: Boolean = false

        override fun remove(key: String) {
            if (failRemoves) error("disk remove failed")
            delegate.remove(key)
        }
    }
}
