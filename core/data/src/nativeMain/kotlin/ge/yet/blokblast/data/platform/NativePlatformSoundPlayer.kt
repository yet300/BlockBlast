package ge.yet.blokblast.data.platform

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.domain.model.FeedbackType
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

/**
 * iOS actual — plays bundled .caf/.m4a SFX via [AVAudioPlayer]. Missing assets
 * are silently ignored. Players are cached per-resource to minimise allocation.
 *
 * `internal` — never leaks out of `:shared`.
 */
@SingleIn(AppScope::class)
@Inject
internal class NativePlatformSoundPlayer() : PlatformSoundPlayer {

    private val cache: MutableMap<String, AVAudioPlayer> = mutableMapOf()

    override fun playPlacement() = safePlay("block_place")
    override fun playClear(lines: Int) = safePlay("line_clear_${lines.coerceAtMost(4)}")
    override fun playVoiceFeedback(type: FeedbackType) = safePlay("voice_${type.name.lowercase()}")
    override fun playVoiceCombo(combo: Int) = safePlay("voice_combo_${combo.coerceAtMost(10)}")

    override fun release() {
        cache.values.forEach { it.stop() }
        cache.clear()
    }

    private fun safePlay(resource: String) {
        val player = cache.getOrPut(resource) { load(resource) ?: return }
        player.currentTime = 0.0
        player.play()
    }

    @OptIn(ExperimentalForeignApi::class)
    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun load(resource: String): AVAudioPlayer? {
        val url: NSURL = NSBundle.mainBundle.URLForResource(resource, withExtension = "caf")
            ?: NSBundle.mainBundle.URLForResource(resource, withExtension = "m4a")
            ?: return null
        return runCatching { AVAudioPlayer(contentsOfURL = url, error = null) }.getOrNull()
            ?.also { it.prepareToPlay() }
    }
}
