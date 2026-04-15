package ge.yet.blokblast.data.platform

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

/**
 * iOS actual — uses [UIImpactFeedbackGenerator] for taptic feedback.
 * Generators are lazily created and kept warm (`prepare()`) for low latency.
 *
 * `internal` — only consumed by [ge.yet.blokblast.data.repository.DefaultVibrationRepository] inside `:shared`.
 */
@SingleIn(AppScope::class)
@Inject
internal class NativePlatformVibrator() : PlatformVibrator {

    private val lightGenerator by lazy {
        UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).also {
            it.prepare()
        }
    }

    private val heavyGenerator by lazy {
        UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy).also {
            it.prepare()
        }
    }

    override fun light() {
        lightGenerator.impactOccurred()
        lightGenerator.prepare()
    }

    override fun heavy() {
        heavyGenerator.impactOccurred()
        heavyGenerator.prepare()
    }
}
