package ge.yet.game.twentyfortyeight.diagnostics

import ge.yet.game.domain.repository.CrashlyticsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CrashlyticsTwentyFortyEightDiagnosticsTest {
    @Test
    fun `every closed failure maps to its exact sanitized message`() {
        val crashlytics = RecordingCrashlytics()
        val diagnostics = CrashlyticsTwentyFortyEightDiagnostics(crashlytics)
        val mappings = listOf(
            TwentyFortyEightFailure.StorageRead(StorageOperation.CurrentGameRead) to
                "storage_read:current_game_read",
            TwentyFortyEightFailure.StorageRead(StorageOperation.BestScoreRead) to
                "storage_read:best_score_read",
            TwentyFortyEightFailure.StorageRead(StorageOperation.StatisticsRead) to
                "storage_read:statistics_read",
            TwentyFortyEightFailure.StorageRead(StorageOperation.TutorialRead) to
                "storage_read:tutorial_read",
            TwentyFortyEightFailure.StorageWrite(StorageOperation.CurrentGameWrite) to
                "storage_write:current_game_write",
            TwentyFortyEightFailure.StorageWrite(StorageOperation.BestScoreWrite) to
                "storage_write:best_score_write",
            TwentyFortyEightFailure.StorageWrite(StorageOperation.StatisticsWrite) to
                "storage_write:statistics_write",
            TwentyFortyEightFailure.StorageWrite(StorageOperation.TutorialWrite) to
                "storage_write:tutorial_write",
            TwentyFortyEightFailure.ContractViolation(ContractCode.SnapshotShape) to
                "contract_violation:snapshot_shape",
            TwentyFortyEightFailure.ContractViolation(ContractCode.SnapshotVersion) to
                "contract_violation:snapshot_version",
            TwentyFortyEightFailure.ContractViolation(ContractCode.RngAlgorithm) to
                "contract_violation:rng_algorithm",
            TwentyFortyEightFailure.InvariantViolation(InvariantCode.UnsupportedTile) to
                "invariant_violation:unsupported_tile",
            TwentyFortyEightFailure.InvariantViolation(InvariantCode.NegativeCounter) to
                "invariant_violation:negative_counter",
            TwentyFortyEightFailure.InvariantViolation(InvariantCode.RevisionRegression) to
                "invariant_violation:revision_regression",
            TwentyFortyEightFailure.InvariantViolation(InvariantCode.BarrierPending) to
                "invariant_violation:barrier_pending",
            TwentyFortyEightFailure.InvariantViolation(InvariantCode.ScoreOverflow) to
                "invariant_violation:score_overflow",
            TwentyFortyEightFailure.InvariantViolation(InvariantCode.IdentityOverflow) to
                "invariant_violation:identity_overflow",
        )

        mappings.forEach { (failure, _) -> diagnostics.record(failure) }

        assertEquals(mappings.size, crashlytics.exceptions.size)
        mappings.zip(crashlytics.exceptions).forEach { (mapping, throwable) ->
            val exception = assertIs<TwentyFortyEightDiagnosticException>(throwable)
            assertEquals(mapping.second, exception.message)
            assertTrue(exception.cause == null)
            assertTrue(exception.message.orEmpty().matches(FIXED_MESSAGE_PATTERN))
        }
        crashlytics.assertNoOtherCalls()
    }

    @Test
    fun `one typed failure emits one exception and retains no adapter state`() {
        val crashlytics = RecordingCrashlytics()
        val first = CrashlyticsTwentyFortyEightDiagnostics(crashlytics)
        val second = CrashlyticsTwentyFortyEightDiagnostics(crashlytics)
        val failure = TwentyFortyEightFailure.StorageWrite(StorageOperation.CurrentGameWrite)

        first.record(failure)
        second.record(failure)

        assertEquals(
            listOf("storage_write:current_game_write", "storage_write:current_game_write"),
            crashlytics.exceptions.map(Throwable::message),
        )
        crashlytics.assertNoOtherCalls()
    }

    @Test
    fun `constructors accept only typed game failure and core crashlytics contract`() {
        val exceptionFactory:
            (TwentyFortyEightFailure) -> TwentyFortyEightDiagnosticException =
            ::TwentyFortyEightDiagnosticException
        val diagnosticsFactory:
            (CrashlyticsRepository) -> CrashlyticsTwentyFortyEightDiagnostics =
            ::CrashlyticsTwentyFortyEightDiagnostics
        val crashlytics = RecordingCrashlytics()
        val failure = TwentyFortyEightFailure.InvariantViolation(InvariantCode.ScoreOverflow)

        val exception = exceptionFactory(failure)
        diagnosticsFactory(crashlytics).record(failure)

        assertEquals("invariant_violation:score_overflow", exception.message)
        assertEquals("invariant_violation:score_overflow", crashlytics.exceptions.single().message)
        crashlytics.assertNoOtherCalls()
    }

    private class RecordingCrashlytics : CrashlyticsRepository {
        val exceptions = mutableListOf<Throwable>()
        val userIds = mutableListOf<String>()
        var clearUserCalls = 0
        val customValues = mutableListOf<Pair<String, Any>>()
        val messages = mutableListOf<String>()

        override fun setUserID(id: String) {
            userIds += id
        }

        override fun clearUserID() {
            clearUserCalls += 1
        }

        override fun setCustomValue(key: String, value: Any) {
            customValues += key to value
        }

        override fun logException(throwable: Throwable) {
            exceptions += throwable
        }

        override fun logMessage(message: String) {
            messages += message
        }

        fun assertNoOtherCalls() {
            assertTrue(userIds.isEmpty())
            assertEquals(0, clearUserCalls)
            assertTrue(customValues.isEmpty())
            assertTrue(messages.isEmpty())
        }
    }

    private companion object {
        val FIXED_MESSAGE_PATTERN = Regex("[a-z_]+:[a-z_]+")
    }
}
