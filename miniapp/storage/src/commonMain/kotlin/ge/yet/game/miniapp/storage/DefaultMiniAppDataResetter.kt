package ge.yet.game.miniapp.storage

import com.app.common.AppDispatchers
import com.russhwolf.settings.ObservableSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.domain.repository.CrashlyticsRepository
import ge.yet.game.miniapp.api.MiniAppAdditionalDataCleaner
import ge.yet.game.miniapp.api.MiniAppDataResetResult
import ge.yet.game.miniapp.api.MiniAppDataResetter
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppLegacyStorageKeys
import ge.yet.game.miniapp.api.requireValid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@Inject
@SingleIn(AppScope::class)
internal class DefaultMiniAppDataResetter(
    private val settings: ObservableSettings,
    private val dispatchers: AppDispatchers,
    legacyStorageKeys: Set<MiniAppLegacyStorageKeys>,
    additionalDataCleaners: Set<MiniAppAdditionalDataCleaner>,
    private val crashlytics: CrashlyticsRepository,
) : MiniAppDataResetter {
    private val legacyKeysById = legacyStorageKeys
        .groupBy(MiniAppLegacyStorageKeys::miniAppId)
        .mapValues { (_, declarations) ->
            declarations.flatMap { it.localToPhysicalKeys.values }.distinct()
        }
    private val cleanersById = additionalDataCleaners
        .onEach { it.miniAppId.requireValid() }
        .groupBy(MiniAppAdditionalDataCleaner::miniAppId)

    override suspend fun clear(miniAppIds: Set<MiniAppId>): MiniAppDataResetResult {
        val ids = buildSet { miniAppIds.forEach { add(it.also(MiniAppId::requireValid)) } }
            .sortedBy(MiniAppId::value)
        val failures = linkedMapOf<MiniAppId, Exception>()

        ids.forEach { id ->
            clearNamespace(id, failures)
            legacyKeysById[id].orEmpty().forEach { physicalKey ->
                attempt(id, failures) {
                    withContext(dispatchers.io) { settings.remove(physicalKey) }
                }
            }
            cleanersById[id].orEmpty().forEach { cleaner ->
                attempt(id, failures, cleaner::clear)
            }
        }

        val failedIds = failures.keys.toSet()
        failedIds.forEach { id ->
            runCatching { crashlytics.logMessage("MiniApp data reset failed ${id.value}") }
        }
        return if (failedIds.isEmpty()) {
            MiniAppDataResetResult.Success
        } else {
            MiniAppDataResetResult.PartialFailure(failedIds)
        }
    }

    private suspend fun clearNamespace(
        id: MiniAppId,
        failures: MutableMap<MiniAppId, Exception>,
    ) {
        val prefix = "miniapp.${id.value}."
        val keys = try {
            withContext(dispatchers.io) { settings.keys.filter { it.startsWith(prefix) } }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            if (id !in failures) failures[id] = failure
            return
        }
        keys.forEach { physicalKey ->
            attempt(id, failures) {
                withContext(dispatchers.io) { settings.remove(physicalKey) }
            }
        }
    }

    private suspend fun attempt(
        id: MiniAppId,
        failures: MutableMap<MiniAppId, Exception>,
        operation: suspend () -> Unit,
    ) {
        try {
            operation()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            if (id !in failures) failures[id] = failure
        }
    }
}
