package ge.yet.blokblast.data.platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import kotlin.getValue

/**
 * iOS actual — uses [UIImpactFeedbackGenerator] for taptic feedback.
 * Generators are lazily created and kept warm (`prepare()`) for low latency.
 *
 * `internal` — only consumed by [ge.yet.blokblast.data.repository.DefaultVibrationRepository] inside `:shared`.
 */
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
