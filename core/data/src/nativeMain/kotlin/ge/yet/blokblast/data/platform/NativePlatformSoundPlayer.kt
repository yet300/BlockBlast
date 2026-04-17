package ge.yet.blokblast.data.platform

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.domain.model.FeedbackType
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

/**
 * iOS actual — plays bundled SFX via [AVAudioPlayer].
 *
 * Audio files live in a single place: `composeApp/src/commonMain/composeResources/files/audio/`.
 * Compose Multiplatform copies those files into the app bundle under
 * `compose-resources/blockblast.composeapp.generated.resources/files/audio/`,
 * which is accessed via [NSBundle.mainBundle.pathForResource].
 *
 * Players are cached per-resource to minimise allocation.
 * Background music loops at reduced volume via a dedicated [AVAudioPlayer].
 * `internal` — never leaks out of `:shared`.
 */
@OptIn(ExperimentalForeignApi::class)
@SingleIn(AppScope::class)
@Inject
internal class NativePlatformSoundPlayer() : PlatformSoundPlayer {

    private val sfxCache: MutableMap<String, AVAudioPlayer> = mutableMapOf()
    private var musicPlayer: AVAudioPlayer? = null

    init {
        // Allow audio to play even when the silent switch is off
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
        }
    }

    override fun playPlacement() = safePlay("block_place")
    override fun playClear(lines: Int) = safePlay("line_clear_${lines.coerceAtMost(4)}")
    override fun playVoiceFeedback(type: FeedbackType) = safePlay("voice_${type.name.lowercase()}")

    override fun playVoiceCombo(combo: Int) {
        // Try specific file first (voice_combo_1…10), fall back to voice_amazing
        val specific = "voice_combo_${combo.coerceAtMost(10)}"
        if (!safePlayReturning(specific)) safePlay("voice_amazing")
    }

    override fun startMusic() {
        if (musicPlayer?.playing == true) return
        val player = loadPlayer("music_ambient") ?: return
        player.numberOfLoops = -1   // loop forever
        player.volume = MUSIC_VOLUME
        musicPlayer = player
        player.play()
    }

    override fun stopMusic() {
        musicPlayer?.stop()
        musicPlayer = null
    }

    override fun release() {
        stopMusic()
        sfxCache.values.forEach { it.stop() }
        sfxCache.clear()
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private fun safePlay(resource: String) {
        safePlayReturning(resource)
    }

    private fun safePlayReturning(resource: String): Boolean {
        val player = sfxCache.getOrPut(resource) { loadPlayer(resource) ?: return false }
        player.currentTime = 0.0
        return player.play()
    }

    private fun loadPlayer(resource: String): AVAudioPlayer? {
        for (ext in listOf("wav", "mp3", "caf", "m4a")) {
            // CMP places composeResources files in the bundle under compose-resources/
            val path = NSBundle.mainBundle.pathForResource(
                name = resource,
                ofType = ext,
                inDirectory = AUDIO_DIR,
            ) ?: continue
            val url = NSURL.fileURLWithPath(path)
            return runCatching { AVAudioPlayer(contentsOfURL = url, error = null) }
                .getOrNull()
                ?.also { it.prepareToPlay() } ?: continue
        }
        return null
    }

    private companion object {
        const val MUSIC_VOLUME = 0.4f

        /**
         * CMP places `composeResources/files/` into the iOS bundle under this path.
         * The directory name follows the pattern: `compose-resources/{moduleId}/files/{subpath}`.
         */
        const val AUDIO_DIR =
            "compose-resources/blockblast.composeapp.generated.resources/files/audio"
    }
}
