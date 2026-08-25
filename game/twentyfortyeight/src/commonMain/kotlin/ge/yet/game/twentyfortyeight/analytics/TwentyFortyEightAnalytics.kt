package ge.yet.game.twentyfortyeight.analytics

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.domain.repository.AnalyticRepository

internal enum class RestartSource {
    Playing,
    Victory,
    Result,
}

internal sealed interface AnalyticsFact {
    data class GameStarted(val runOrdinal: Long) : AnalyticsFact {
        init {
            require(runOrdinal > 0L) { "Run ordinal must be positive: $runOrdinal" }
        }
    }

    data class GameResumed(val runOrdinal: Long) : AnalyticsFact {
        init {
            require(runOrdinal > 0L) { "Run ordinal must be positive: $runOrdinal" }
        }
    }

    data class TutorialCompleted(val skipped: Boolean) : AnalyticsFact

    data class UndoUsed(val runOrdinal: Long) : AnalyticsFact {
        init {
            require(runOrdinal > 0L) { "Run ordinal must be positive: $runOrdinal" }
        }
    }

    data class MilestoneReached(val value: Long) : AnalyticsFact

    data class NewBest(val scoreBucket: Long) : AnalyticsFact {
        init {
            require(scoreBucket in AnalyticsBucketPolicy.scoreBuckets) {
                "Score bucket must be in ${AnalyticsBucketPolicy.scoreBuckets}: $scoreBucket"
            }
        }
    }

    data class Victory(val scoreBucket: Long) : AnalyticsFact {
        init {
            require(scoreBucket in AnalyticsBucketPolicy.scoreBuckets) {
                "Score bucket must be in ${AnalyticsBucketPolicy.scoreBuckets}: $scoreBucket"
            }
        }
    }

    data class Continued(val runOrdinal: Long) : AnalyticsFact {
        init {
            require(runOrdinal > 0L) { "Run ordinal must be positive: $runOrdinal" }
        }
    }

    data class GameOver(val scoreBucket: Long) : AnalyticsFact {
        init {
            require(scoreBucket in AnalyticsBucketPolicy.scoreBuckets) {
                "Score bucket must be in ${AnalyticsBucketPolicy.scoreBuckets}: $scoreBucket"
            }
        }
    }

    data class Restart(val source: RestartSource) : AnalyticsFact
}

internal object AnalyticsBucketPolicy {
    val scoreBuckets: LongRange = 0L..62L

    fun score(score: Long): Long {
        require(score >= 0L) { "Score must be non-negative: $score" }
        if (score == 0L) return 0L
        return (Long.SIZE_BITS - 1 - score.countLeadingZeroBits()).toLong()
    }
}

@Inject
@SingleIn(AppScope::class)
internal class TwentyFortyEightAnalytics(
    private val repository: AnalyticRepository,
) {
    fun log(fact: AnalyticsFact) {
        when (fact) {
            is AnalyticsFact.GameStarted -> emit(
                eventName = "game_started",
                "run_ordinal" to fact.runOrdinal,
            )
            is AnalyticsFact.GameResumed -> emit(
                eventName = "game_resumed",
                "run_ordinal" to fact.runOrdinal,
            )
            is AnalyticsFact.TutorialCompleted -> if (fact.skipped) {
                emit(eventName = "tutorial_skipped", "reason" to "skip")
            } else {
                emit(eventName = "tutorial_completed", "reason" to "move")
            }
            is AnalyticsFact.UndoUsed -> emit(
                eventName = "undo_used",
                "run_ordinal" to fact.runOrdinal,
            )
            is AnalyticsFact.MilestoneReached -> if (fact.value in MILESTONES) {
                emit(eventName = "milestone_tile_reached", "milestone" to fact.value)
            }
            is AnalyticsFact.NewBest -> emit(
                eventName = "new_best",
                "score_bucket" to fact.scoreBucket,
            )
            is AnalyticsFact.Victory -> emit(
                eventName = "victory",
                "score_bucket" to fact.scoreBucket,
            )
            is AnalyticsFact.Continued -> emit(
                eventName = "continue_after_victory",
                "run_ordinal" to fact.runOrdinal,
            )
            is AnalyticsFact.GameOver -> emit(
                eventName = "game_over",
                "score_bucket" to fact.scoreBucket,
            )
            is AnalyticsFact.Restart -> emit(
                eventName = "restart",
                "source" to fact.source.fixedValue,
            )
        }
    }

    private fun emit(
        eventName: String,
        vararg params: Pair<String, Any>,
    ) {
        repository.logEvent(
            eventName = eventName,
            params = mapOf("mini_app_id" to MINI_APP_ID, *params),
        )
    }

    private companion object {
        const val MINI_APP_ID = "game.twentyfortyeight"
        val MILESTONES = setOf(128L, 256L, 512L, 1_024L, 2_048L, 4_096L, 8_192L, 16_384L)
    }
}

private val RestartSource.fixedValue: String
    get() = when (this) {
        RestartSource.Playing -> "playing"
        RestartSource.Victory -> "victory"
        RestartSource.Result -> "result"
    }
