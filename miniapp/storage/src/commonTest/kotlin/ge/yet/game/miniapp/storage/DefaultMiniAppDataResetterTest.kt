package ge.yet.game.miniapp.storage

import com.app.common.AppDispatchers
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.observable.makeObservable
import ge.yet.game.domain.repository.CrashlyticsRepository
import ge.yet.game.miniapp.api.MiniAppAdditionalDataCleaner
import ge.yet.game.miniapp.api.MiniAppDataResetResult
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppLegacyStorageKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalSettingsApi::class)
class DefaultMiniAppDataResetterTest {
    @Test
    fun clear_removes_only_requested_namespaces_and_matching_legacy_aliases() = runTest {
        val settings = MapSettings(
            "host.theme" to "dark",
            "miniapp.game.blocks.score" to 7L,
            "miniapp.game.snake.score" to 9L,
            "blockblast.game_save" to "save",
            "blockblast.best_score" to 11L,
            "blockblast.tutorial_seen" to true,
        )
        val resetter = resetter(
            settings = settings,
            legacyKeys = setOf(blockBlastLegacyKeys()),
        )

        assertEquals(
            MiniAppDataResetResult.Success,
            resetter.clear(setOf(MiniAppId("game.blocks"))),
        )

        assertEquals(
            setOf(
                "host.theme",
                "miniapp.game.snake.score",
                "blockblast.game_save",
                "blockblast.best_score",
                "blockblast.tutorial_seen",
            ),
            settings.keys,
        )

        assertEquals(
            MiniAppDataResetResult.Success,
            resetter.clear(setOf(MiniAppId("game.blockblast"))),
        )
        assertEquals(setOf("host.theme", "miniapp.game.snake.score"), settings.keys)
    }

    @Test
    fun cleaners_run_independently_and_failures_are_aggregated_by_sorted_id() = runTest {
        val first = RecordingCleaner(MiniAppId("game.alpha"), failure = IllegalStateException("alpha"))
        val second = RecordingCleaner(MiniAppId("game.beta"))
        val third = RecordingCleaner(MiniAppId("game.beta"), failure = IllegalStateException("beta"))
        val crashlytics = RecordingCrashlytics()
        val resetter = resetter(
            settings = MapSettings(),
            cleaners = setOf(third, first, second),
            crashlytics = crashlytics,
        )

        val result = resetter.clear(
            linkedSetOf(MiniAppId("game.beta"), MiniAppId("game.alpha"), MiniAppId("game.clean")),
        )

        assertEquals(
            setOf(MiniAppId("game.alpha"), MiniAppId("game.beta")),
            (result as MiniAppDataResetResult.PartialFailure).failedMiniAppIds,
        )
        assertEquals(1, first.clearCount)
        assertEquals(1, second.clearCount)
        assertEquals(1, third.clearCount)
        assertEquals(listOf("game.alpha", "game.beta"), crashlytics.failedIds)
    }

    @Test
    fun cancellation_is_rethrown_and_not_reported() = runTest {
        val crashlytics = RecordingCrashlytics()
        val resetter = resetter(
            settings = MapSettings(),
            cleaners = setOf(
                RecordingCleaner(
                    miniAppId = MiniAppId("game.blocks"),
                    failure = CancellationException("cancel"),
                ),
            ),
            crashlytics = crashlytics,
        )

        assertFailsWith<CancellationException> {
            resetter.clear(setOf(MiniAppId("game.blocks")))
        }
        assertEquals(emptyList(), crashlytics.failedIds)
    }

    @Test
    fun repeated_reset_is_successful() = runTest {
        val settings = MapSettings("miniapp.game.blocks.score" to 7L)
        val resetter = resetter(settings)
        val ids = setOf(MiniAppId("game.blocks"))

        assertEquals(MiniAppDataResetResult.Success, resetter.clear(ids))
        assertEquals(MiniAppDataResetResult.Success, resetter.clear(ids))
    }

    private fun resetter(
        settings: MapSettings,
        legacyKeys: Set<MiniAppLegacyStorageKeys> = emptySet(),
        cleaners: Set<MiniAppAdditionalDataCleaner> = emptySet(),
        crashlytics: RecordingCrashlytics = RecordingCrashlytics(),
    ): DefaultMiniAppDataResetter = DefaultMiniAppDataResetter(
        settings = settings.makeObservable(),
        dispatchers = AppDispatchers(
            default = Dispatchers.Unconfined,
            io = Dispatchers.Unconfined,
        ),
        legacyStorageKeys = legacyKeys,
        additionalDataCleaners = cleaners,
        crashlytics = crashlytics,
    )

    private fun blockBlastLegacyKeys() = MiniAppLegacyStorageKeys(
        miniAppId = MiniAppId("game.blockblast"),
        localToPhysicalKeys = mapOf(
            "game_save" to "blockblast.game_save",
            "best_score" to "blockblast.best_score",
            "tutorial_seen" to "blockblast.tutorial_seen",
        ),
    )

    private class RecordingCleaner(
        override val miniAppId: MiniAppId,
        private val failure: Throwable? = null,
    ) : MiniAppAdditionalDataCleaner {
        var clearCount = 0
            private set

        override suspend fun clear() {
            clearCount += 1
            failure?.let { throw it }
        }
    }

    private class RecordingCrashlytics : CrashlyticsRepository {
        val failedIds = mutableListOf<String>()

        override fun setUserID(id: String) = Unit
        override fun clearUserID() = Unit
        override fun setCustomValue(key: String, value: Any) = Unit
        override fun logException(throwable: Throwable) = Unit
        override fun logMessage(message: String) {
            failedIds += message.substringAfterLast(' ')
        }
    }
}
