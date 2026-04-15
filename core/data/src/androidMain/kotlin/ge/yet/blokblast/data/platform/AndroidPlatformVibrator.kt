package ge.yet.blokblast.data.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Android actual — dispatches one-shot [VibrationEffect]s via the platform vibrator.
 * Handles both the modern [VibratorManager] (API 31+) and the legacy API.
 *
 * `internal` — only used by [ge.yet.blokblast.data.repository.DefaultVibrationRepository].
 * [Context] is supplied by the Metro graph via `@BindsInstance`.
 */
@SingleIn(AppScope::class)
@Inject
internal class AndroidPlatformVibrator(
    private val ctx: Context,
) : PlatformVibrator {

    private val vibrator: Vibrator? = resolveVibrator()

    override fun light() = vibrate(LIGHT_MS, LIGHT_AMPLITUDE)
    override fun heavy() = vibrate(HEAVY_MS, HEAVY_AMPLITUDE)

    @Suppress("DEPRECATION")
    private fun vibrate(durationMs: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            v.vibrate(durationMs)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private companion object {
        const val LIGHT_MS = 15L
        const val HEAVY_MS = 40L
        const val LIGHT_AMPLITUDE = 80
        const val HEAVY_AMPLITUDE = 200
    }
}
