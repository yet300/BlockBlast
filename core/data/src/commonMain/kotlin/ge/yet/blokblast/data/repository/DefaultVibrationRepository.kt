package ge.yet.blokblast.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.data.platform.PlatformVibrator
import ge.yet.blokblast.domain.repository.SettingsRepository
import ge.yet.blokblast.domain.repository.VibrationRepository


/**
 * Guards every haptic call with the live `vibrationEnabled` flag, then
 * delegates to the platform bridge.
 *
 * `internal` — only the [VibrationRepository] interface is exposed.
 */
@SingleIn(AppScope::class)
@Inject
internal class DefaultVibrationRepository(
    private val vibrator: PlatformVibrator,
    private val settings: SettingsRepository,
) : VibrationRepository {

    private inline fun ifEnabled(block: () -> Unit) {
        if (settings.vibrationEnabled.value) block()
    }

    override suspend fun vibrateLight() = ifEnabled { vibrator.light() }
    override suspend fun vibrateHeavy() = ifEnabled { vibrator.heavy() }
}
