package ge.yet.game.twentyfortyeight

import ge.yet.game.miniapp.api.MiniAppSnapshotSpec
import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.testkit.MutableMiniAppStorage
import ge.yet.game.twentyfortyeight.engine.GameRules
import ge.yet.game.twentyfortyeight.engine.GameStatistics
import ge.yet.game.twentyfortyeight.engine.RngState
import ge.yet.game.twentyfortyeight.persistence.GameCommit
import ge.yet.game.twentyfortyeight.persistence.LoadResult
import ge.yet.game.twentyfortyeight.persistence.TwentyFortyEightPersistence
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TwentyFortyEightStorageNamespaceTest {
    @Test
    fun `persistence uses only the four approved local snapshot names`() = runTest {
        val storage = RecordingStorage()

        TwentyFortyEightPersistence().commit(storage, commit(revision = 1L, bestScore = 128L))

        assertEquals(
            setOf("current_game", "best_score", "statistics", "tutorial_seen"),
            storage.touchedLocalNames,
        )
    }

    @Test
    fun `resetting one provided namespace cannot clear another session namespace`() = runTest {
        val persistence = TwentyFortyEightPersistence()
        val first = RecordingStorage()
        val second = RecordingStorage()
        persistence.commit(first, commit(revision = 1L, bestScore = 128L))
        persistence.commit(second, commit(revision = 2L, bestScore = 256L))

        first.clear()

        val firstLoad = assertIs<LoadResult.Loaded>(persistence.load(first))
        val secondLoad = assertIs<LoadResult.Loaded>(persistence.load(second))
        assertEquals(0L, firstLoad.data.bestScore)
        assertEquals(256L, secondLoad.data.bestScore)
        assertEquals(
            setOf("current_game", "best_score", "statistics", "tutorial_seen"),
            second.touchedLocalNames,
        )
    }
}

private class RecordingStorage(
    private val delegate: MutableMiniAppStorage = MutableMiniAppStorage(),
) : MiniAppStorage by delegate {
    private val mutableTouchedLocalNames = linkedSetOf<String>()
    val touchedLocalNames: Set<String>
        get() = mutableTouchedLocalNames.toSet()

    override suspend fun <T> readSnapshot(localName: String, spec: MiniAppSnapshotSpec<T>): T? {
        mutableTouchedLocalNames += localName
        return delegate.readSnapshot(localName, spec)
    }

    override suspend fun <T> writeSnapshot(
        localName: String,
        value: T,
        spec: MiniAppSnapshotSpec<T>,
    ) {
        mutableTouchedLocalNames += localName
        delegate.writeSnapshot(localName, value, spec)
    }

    suspend fun clear() = delegate.clear()
}

private fun commit(revision: Long, bestScore: Long): GameCommit {
    val game = GameRules.newGame(previous = null, seed = RngState.fromBits(revision.toULong())).game
        .copy(bestScore = bestScore)
    return GameCommit(
        revision = revision,
        game = game,
        bestScore = bestScore,
        statistics = GameStatistics(gamesStarted = revision, highestTileEver = 4L),
        tutorialSeen = false,
        tutorialReason = null,
    )
}
