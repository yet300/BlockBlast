package ge.yet.game.twentyfortyeight.persistence

import ge.yet.game.miniapp.api.MiniAppSnapshotSpec
import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.testkit.NoopMiniAppStorage
import ge.yet.game.twentyfortyeight.diagnostics.ContractCode
import ge.yet.game.twentyfortyeight.diagnostics.InvariantCode
import ge.yet.game.twentyfortyeight.diagnostics.StorageOperation
import ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightFailure
import ge.yet.game.twentyfortyeight.engine.GameStatistics
import ge.yet.game.twentyfortyeight.engine.TutorialCompletionReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwentyFortyEightPersistenceTest {
    private val persistence = TwentyFortyEightPersistence()

    @Test
    fun `commit writes current game first then selected metadata in stable order`() = runTest {
        val storage = RecordingMiniAppStorage()

        persistence.commit(storage, gameCommit(revision = 8L))

        assertEquals(
            listOf("current_game", "best_score", "statistics", "tutorial_seen"),
            storage.writes,
        )
        val current = storage.snapshot("current_game", TwentyFortyEightSchemas.currentGame)
        assertEquals(8L, current?.revision)
        assertEquals(current?.revision, current?.bestMirror?.revision)
        assertEquals(current?.revision, current?.statisticsMirror?.revision)
        assertEquals(current?.revision, current?.tutorialMirror?.revision)
    }

    @Test
    fun `unchanged dedicated metadata is omitted after the complete mirror`() = runTest {
        val storage = RecordingMiniAppStorage()

        persistence.commit(
            storage,
            gameCommit(metadataWrites = setOf(MetadataRecord.Statistics)),
        )

        assertEquals(listOf("current_game", "statistics"), storage.writes)
    }

    @Test
    fun `first write failure writes no metadata and reports exact operation`() = runTest {
        val storage = RecordingMiniAppStorage(failingWrite = "current_game")

        val failure = runCatching { persistence.commit(storage, gameCommit()) }.exceptionOrNull()

        assertEquals(listOf("current_game"), storage.writes)
        assertEquals(
            TwentyFortyEightFailure.StorageWrite(StorageOperation.CurrentGameWrite),
            assertIs<PersistenceWriteException>(failure).failure,
        )
    }

    @Test
    fun `later metadata failure leaves complete recovery mirrors in current game`() = runTest {
        val storage = RecordingMiniAppStorage(failingWrite = "statistics")
        val commit = gameCommit(revision = 12L)

        val failure = runCatching { persistence.commit(storage, commit) }.exceptionOrNull()

        assertEquals(listOf("current_game", "best_score", "statistics"), storage.writes)
        assertEquals(
            TwentyFortyEightFailure.StorageWrite(StorageOperation.StatisticsWrite),
            assertIs<PersistenceWriteException>(failure).failure,
        )
        val mirror = requireNotNull(storage.snapshot("current_game", TwentyFortyEightSchemas.currentGame))
        assertEquals(commit.bestScore, mirror.bestMirror.bestScore)
        assertEquals(commit.statistics.successfulMoves, mirror.statisticsMirror.successfulMoves)
        assertEquals(requireNotNull(commit.tutorialReason).name.uppercase(), mirror.tutorialMirror.reason)
    }

    @Test
    fun `load reconciles newer metadata and monotonic mirrors`() = runTest {
        val current = currentGameV1(revision = 7L)
        val storage = RecordingMiniAppStorage(
            initialSnapshots = mapOf(
                "current_game" to current,
                "best_score" to BestScoreV1(revision = 9L, bestScore = 4096L),
                "statistics" to statisticsV1(revision = 8L).copy(successfulMoves = 20L),
                "tutorial_seen" to TutorialV1(10L, seen = true, reason = "SKIP"),
            ),
        )

        val result = assertIs<LoadResult.Loaded>(persistence.load(storage))
        val loaded = result.data

        assertEquals(listOf("current_game", "best_score", "statistics", "tutorial_seen"), storage.reads)
        assertEquals(10L, loaded.revision)
        assertEquals(4096L, loaded.bestScore)
        assertEquals(4096L, loaded.game?.bestScore)
        assertEquals(20L, loaded.statistics.successfulMoves)
        assertTrue(loaded.tutorialSeen)
        assertEquals(TutorialCompletionReason.Skip, loaded.tutorialReason)
        assertTrue(loaded.terminal)
        assertNull(loaded.game?.undoLineage)
        assertTrue(result.validationFailures.isEmpty())
    }

    @Test
    fun `missing current game still recovers monotonic metadata`() = runTest {
        val storage = RecordingMiniAppStorage(
            initialSnapshots = mapOf(
                "best_score" to BestScoreV1(revision = 3L, bestScore = 512L),
                "statistics" to statisticsV1(revision = 4L),
                "tutorial_seen" to TutorialV1(5L, seen = false, reason = null),
            ),
        )

        val result = assertIs<LoadResult.Loaded>(persistence.load(storage))
        val loaded = result.data

        assertNull(loaded.game)
        assertEquals(5L, loaded.revision)
        assertEquals(512L, loaded.bestScore)
        assertEquals(statisticsV1().gamesWon, loaded.statistics.gamesWon)
        assertEquals(false, loaded.terminal)
    }

    @Test
    fun `invalid current game is absent while valid metadata is restored`() = runTest {
        val storage = RecordingMiniAppStorage(
            initialSnapshots = mapOf(
                "current_game" to currentGameV1(revision = 20L, board = List(16) { 3L }),
                "best_score" to BestScoreV1(revision = 8L, bestScore = 4096L),
                "statistics" to statisticsV1(revision = 9L).copy(successfulMoves = 21L),
                "tutorial_seen" to TutorialV1(10L, seen = true, reason = "SKIP"),
            ),
        )

        val result = assertIs<LoadResult.Loaded>(persistence.load(storage))
        val loaded = result.data

        assertNull(loaded.game)
        assertEquals(10L, loaded.revision)
        assertEquals(4096L, loaded.bestScore)
        assertEquals(21L, loaded.statistics.successfulMoves)
        assertTrue(loaded.tutorialSeen)
        assertEquals(TutorialCompletionReason.Skip, loaded.tutorialReason)
        assertEquals(
            setOf(TwentyFortyEightFailure.ContractViolation(ContractCode.SnapshotShape)),
            result.validationFailures,
        )
    }

    @Test
    fun `invalid record improvement is isolated while metadata is restored`() = runTest {
        val storage = RecordingMiniAppStorage(
            initialSnapshots = mapOf(
                "current_game" to currentGameV1(revision = 20L).copy(
                    score = 0L,
                    bestImprovedInRun = true,
                    bestMirror = BestScoreV1(revision = 20L, bestScore = 0L),
                ),
                "best_score" to BestScoreV1(revision = 8L, bestScore = 4096L),
                "statistics" to statisticsV1(revision = 9L).copy(successfulMoves = 21L),
                "tutorial_seen" to TutorialV1(10L, seen = true, reason = "SKIP"),
            ),
        )

        val result = assertIs<LoadResult.Loaded>(persistence.load(storage))

        assertNull(result.data.game)
        assertEquals(10L, result.data.revision)
        assertEquals(4096L, result.data.bestScore)
        assertEquals(21L, result.data.statistics.successfulMoves)
        assertTrue(result.data.tutorialSeen)
        assertEquals(TutorialCompletionReason.Skip, result.data.tutorialReason)
        assertEquals(
            setOf(TwentyFortyEightFailure.ContractViolation(ContractCode.SnapshotShape)),
            result.validationFailures,
        )
    }

    @Test
    fun `invalid dedicated record is absent while other records are restored`() = runTest {
        val storage = RecordingMiniAppStorage(
            initialSnapshots = mapOf(
                "best_score" to BestScoreV1(revision = 6L, bestScore = 512L),
                "statistics" to statisticsV1(revision = 99L).copy(totalMerges = -1L),
                "tutorial_seen" to TutorialV1(7L, seen = true, reason = "MOVE"),
            ),
        )

        val result = assertIs<LoadResult.Loaded>(persistence.load(storage))
        val loaded = result.data

        assertNull(loaded.game)
        assertEquals(7L, loaded.revision)
        assertEquals(512L, loaded.bestScore)
        assertEquals(GameStatistics(), loaded.statistics)
        assertTrue(loaded.tutorialSeen)
        assertEquals(TutorialCompletionReason.Move, loaded.tutorialReason)
        assertEquals(
            setOf(TwentyFortyEightFailure.InvariantViolation(InvariantCode.NegativeCounter)),
            result.validationFailures,
        )
    }

    @Test
    fun `thrown read failure is typed by local record`() = runTest {
        val storage = RecordingMiniAppStorage(failingRead = "statistics")

        val result = assertIs<LoadResult.Failed>(persistence.load(storage))

        assertEquals(
            TwentyFortyEightFailure.StorageRead(StorageOperation.StatisticsRead),
            result.failure,
        )
        assertEquals(listOf("current_game", "best_score", "statistics"), storage.reads)
    }

    @Test
    fun `read and write cancellation propagate unchanged`() = runTest {
        val readCancellation = object : MiniAppStorage by NoopMiniAppStorage {
            override suspend fun <T> readSnapshot(
                localName: String,
                spec: MiniAppSnapshotSpec<T>,
            ): T? = throw CancellationException("read")
        }
        val writeCancellation = object : MiniAppStorage by NoopMiniAppStorage {
            override suspend fun <T> writeSnapshot(
                localName: String,
                value: T,
                spec: MiniAppSnapshotSpec<T>,
            ) = throw CancellationException("write")
        }

        assertFailsWith<CancellationException> { persistence.load(readCancellation) }
        assertFailsWith<CancellationException> { persistence.commit(writeCancellation, gameCommit()) }
    }
}

internal fun gameCommit(
    revision: Long = 7L,
    metadataWrites: Set<MetadataRecord> = MetadataRecord.entries.toSet(),
): GameCommit {
    val payload = currentGameV1(revision = revision)
    val game = TwentyFortyEightSchemas.toDomain(payload).getOrThrow()
    val statistics = TwentyFortyEightSchemas.toStatistics(payload.statisticsMirror).getOrThrow()
    return GameCommit(
        revision = revision,
        game = game,
        bestScore = game.bestScore,
        statistics = statistics,
        tutorialSeen = true,
        tutorialReason = TutorialCompletionReason.Move,
        metadataWrites = metadataWrites,
    )
}
