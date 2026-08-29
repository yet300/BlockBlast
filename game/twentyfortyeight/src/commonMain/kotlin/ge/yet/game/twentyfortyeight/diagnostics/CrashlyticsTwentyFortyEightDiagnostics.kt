package ge.yet.game.twentyfortyeight.diagnostics

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.domain.repository.CrashlyticsRepository

internal class TwentyFortyEightDiagnosticException(
    failure: TwentyFortyEightFailure,
) : IllegalStateException(failure.fixedMessage())

@Inject
@SingleIn(AppScope::class)
internal class CrashlyticsTwentyFortyEightDiagnostics(
    private val crashlytics: CrashlyticsRepository,
) : TwentyFortyEightDiagnostics {
    override fun record(failure: TwentyFortyEightFailure) {
        crashlytics.logException(TwentyFortyEightDiagnosticException(failure))
    }
}

private fun TwentyFortyEightFailure.fixedMessage(): String = when (this) {
    is TwentyFortyEightFailure.StorageRead -> "storage_read:${operation.fixedCode}"
    is TwentyFortyEightFailure.StorageWrite -> "storage_write:${operation.fixedCode}"
    is TwentyFortyEightFailure.ContractViolation -> "contract_violation:${code.fixedCode}"
    is TwentyFortyEightFailure.InvariantViolation -> "invariant_violation:${code.fixedCode}"
}

private val StorageOperation.fixedCode: String
    get() = when (this) {
        StorageOperation.CurrentGameRead -> "current_game_read"
        StorageOperation.BestScoreRead -> "best_score_read"
        StorageOperation.StatisticsRead -> "statistics_read"
        StorageOperation.TutorialRead -> "tutorial_read"
        StorageOperation.CurrentGameWrite -> "current_game_write"
        StorageOperation.BestScoreWrite -> "best_score_write"
        StorageOperation.StatisticsWrite -> "statistics_write"
        StorageOperation.TutorialWrite -> "tutorial_write"
    }

private val ContractCode.fixedCode: String
    get() = when (this) {
        ContractCode.SnapshotShape -> "snapshot_shape"
        ContractCode.SnapshotVersion -> "snapshot_version"
        ContractCode.RngAlgorithm -> "rng_algorithm"
    }

private val InvariantCode.fixedCode: String
    get() = when (this) {
        InvariantCode.UnsupportedTile -> "unsupported_tile"
        InvariantCode.NegativeCounter -> "negative_counter"
        InvariantCode.RevisionRegression -> "revision_regression"
        InvariantCode.BarrierPending -> "barrier_pending"
        InvariantCode.PendingFactOverflow -> "pending_fact_overflow"
        InvariantCode.CounterOverflow -> "counter_overflow"
        InvariantCode.ScoreOverflow -> "score_overflow"
        InvariantCode.IdentityOverflow -> "identity_overflow"
    }
