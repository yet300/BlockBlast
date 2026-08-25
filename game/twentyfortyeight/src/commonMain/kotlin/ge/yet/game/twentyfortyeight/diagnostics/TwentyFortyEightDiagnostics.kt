package ge.yet.game.twentyfortyeight.diagnostics

internal enum class StorageOperation {
    CurrentGameRead,
    BestScoreRead,
    StatisticsRead,
    TutorialRead,
    CurrentGameWrite,
    BestScoreWrite,
    StatisticsWrite,
    TutorialWrite,
}

internal enum class ContractCode {
    SnapshotShape,
    SnapshotVersion,
    RngAlgorithm,
}

internal enum class InvariantCode {
    UnsupportedTile,
    NegativeCounter,
    RevisionRegression,
    BarrierPending,
    ScoreOverflow,
    IdentityOverflow,
}

internal sealed interface TwentyFortyEightFailure {
    data class StorageRead(val operation: StorageOperation) : TwentyFortyEightFailure
    data class StorageWrite(val operation: StorageOperation) : TwentyFortyEightFailure
    data class ContractViolation(val code: ContractCode) : TwentyFortyEightFailure
    data class InvariantViolation(val code: InvariantCode) : TwentyFortyEightFailure
}

internal fun interface TwentyFortyEightDiagnostics {
    fun record(failure: TwentyFortyEightFailure)
}
