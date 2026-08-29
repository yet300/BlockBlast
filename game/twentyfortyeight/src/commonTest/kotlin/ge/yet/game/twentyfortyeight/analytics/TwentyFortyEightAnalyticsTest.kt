package ge.yet.game.twentyfortyeight.analytics

import ge.yet.game.domain.repository.AnalyticRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TwentyFortyEightAnalyticsTest {
    @Test
    fun `every fact maps to its exact bounded event`() {
        val repository = RecordingAnalytics()
        val analytics = TwentyFortyEightAnalytics(repository)

        listOf(
            AnalyticsFact.GameStarted(runOrdinal = 4L),
            AnalyticsFact.GameResumed(runOrdinal = 5L),
            AnalyticsFact.TutorialCompleted(skipped = false),
            AnalyticsFact.TutorialCompleted(skipped = true),
            AnalyticsFact.UndoUsed(runOrdinal = 6L),
            AnalyticsFact.MilestoneReached(value = 2_048L),
            AnalyticsFact.NewBest(scoreBucket = 8L),
            AnalyticsFact.Victory(scoreBucket = 9L),
            AnalyticsFact.Continued(runOrdinal = 7L),
            AnalyticsFact.GameOver(scoreBucket = 10L),
            AnalyticsFact.Restart(RestartSource.Playing),
            AnalyticsFact.Restart(RestartSource.Victory),
            AnalyticsFact.Restart(RestartSource.Result),
        ).forEach(analytics::log)

        assertEquals(
            listOf(
                Event("game_started", params("run_ordinal" to 4L)),
                Event("game_resumed", params("run_ordinal" to 5L)),
                Event("tutorial_completed", params("reason" to "move")),
                Event("tutorial_skipped", params("reason" to "skip")),
                Event("undo_used", params("run_ordinal" to 6L)),
                Event("milestone_tile_reached", params("milestone" to 2_048L)),
                Event("new_best", params("score_bucket" to 8L)),
                Event("victory", params("score_bucket" to 9L)),
                Event("continue_after_victory", params("run_ordinal" to 7L)),
                Event("game_over", params("score_bucket" to 10L)),
                Event("restart", params("source" to "playing")),
                Event("restart", params("source" to "victory")),
                Event("restart", params("source" to "result")),
            ),
            repository.events,
        )
        assertEquals(0, repository.deleteCalls)
    }

    @Test
    fun `only exact milestone whitelist emits`() {
        val repository = RecordingAnalytics()
        val analytics = TwentyFortyEightAnalytics(repository)
        val milestones = listOf(128L, 256L, 512L, 1_024L, 2_048L, 4_096L, 8_192L, 16_384L)

        listOf(-1L, 0L, 64L, 127L, 129L, 32_768L, Long.MAX_VALUE)
            .forEach { analytics.log(AnalyticsFact.MilestoneReached(it)) }
        milestones.forEach { analytics.log(AnalyticsFact.MilestoneReached(it)) }

        assertEquals(
            milestones.map { Event("milestone_tile_reached", params("milestone" to it)) },
            repository.events,
        )
        assertEquals(0, repository.deleteCalls)
    }

    @Test
    fun `analytics surface contains only allowed scalar keys and values`() {
        val repository = RecordingAnalytics()
        val analytics = TwentyFortyEightAnalytics(repository)

        listOf(
            AnalyticsFact.GameStarted(1L),
            AnalyticsFact.GameResumed(2L),
            AnalyticsFact.TutorialCompleted(false),
            AnalyticsFact.TutorialCompleted(true),
            AnalyticsFact.UndoUsed(3L),
            AnalyticsFact.MilestoneReached(16_384L),
            AnalyticsFact.NewBest(4L),
            AnalyticsFact.Victory(5L),
            AnalyticsFact.Continued(6L),
            AnalyticsFact.GameOver(7L),
            AnalyticsFact.Restart(RestartSource.Result),
        ).forEach(analytics::log)

        repository.events.forEach { event ->
            assertTrue(event.params.keys.all(ALLOWED_ANALYTICS_KEYS::contains))
            assertEquals(MINI_APP_ID, event.params["mini_app_id"])
            assertTrue(
                event.params.values.all { value ->
                    value is String || value is Number || value is Boolean
                },
            )
        }
        assertEquals(0, repository.deleteCalls)
    }

    @Test
    fun `score bucket policy uses finite base two exponent thresholds`() {
        val thresholds = listOf(
            0L to 0L,
            1L to 0L,
            2L to 1L,
            3L to 1L,
            4L to 2L,
            7L to 2L,
            8L to 3L,
            ((1L shl 61) - 1L) to 60L,
            (1L shl 61) to 61L,
            ((1L shl 62) - 1L) to 61L,
            (1L shl 62) to 62L,
            Long.MAX_VALUE to 62L,
        )

        thresholds.forEach { (score, expectedBucket) ->
            assertEquals(expectedBucket, AnalyticsBucketPolicy.score(score), "score=$score")
        }
        assertFailsWith<IllegalArgumentException> { AnalyticsBucketPolicy.score(-1L) }
    }

    @Test
    fun `run ordinal facts reject non-positive contract values`() {
        val invalidFactories = listOf<() -> AnalyticsFact>(
            { AnalyticsFact.GameStarted(0L) },
            { AnalyticsFact.GameStarted(-1L) },
            { AnalyticsFact.GameResumed(0L) },
            { AnalyticsFact.GameResumed(-1L) },
            { AnalyticsFact.UndoUsed(0L) },
            { AnalyticsFact.UndoUsed(-1L) },
            { AnalyticsFact.Continued(0L) },
            { AnalyticsFact.Continued(-1L) },
        )

        invalidFactories.forEach { factory ->
            assertFailsWith<IllegalArgumentException> { factory() }
        }
    }

    @Test
    fun `score facts accept only the sixty three canonical buckets`() {
        val validBuckets = listOf(0L, 1L, 61L, 62L)
        validBuckets.forEach { bucket ->
            AnalyticsFact.NewBest(bucket)
            AnalyticsFact.Victory(bucket)
            AnalyticsFact.GameOver(bucket)
        }

        listOf(-1L, 63L, Long.MAX_VALUE).forEach { invalidBucket ->
            assertFailsWith<IllegalArgumentException> { AnalyticsFact.NewBest(invalidBucket) }
            assertFailsWith<IllegalArgumentException> { AnalyticsFact.Victory(invalidBucket) }
            assertFailsWith<IllegalArgumentException> { AnalyticsFact.GameOver(invalidBucket) }
        }
    }

    private class RecordingAnalytics : AnalyticRepository {
        val events = mutableListOf<Event>()
        var deleteCalls = 0

        override fun logEvent(eventName: String, params: Map<String, Any>?) {
            events += Event(eventName, requireNotNull(params))
        }

        override fun deleteData() {
            deleteCalls += 1
        }
    }

    private data class Event(
        val name: String,
        val params: Map<String, Any>,
    )

    private companion object {
        const val MINI_APP_ID = "game.twentyfortyeight"
        val ALLOWED_ANALYTICS_KEYS = setOf(
            "mini_app_id",
            "run_ordinal",
            "reason",
            "milestone",
            "score_bucket",
            "source",
        )

        fun params(vararg values: Pair<String, Any>): Map<String, Any> =
            mapOf("mini_app_id" to MINI_APP_ID, *values)
    }
}
