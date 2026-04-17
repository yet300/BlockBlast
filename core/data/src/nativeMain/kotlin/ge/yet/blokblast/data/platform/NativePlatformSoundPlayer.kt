package ge.yet.blokblast.data.platform

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.domain.model.FeedbackType
import ge.yet.blokblast.domain.repository.AudioFileProvider
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

/**
 * iOS actual — plays SFX via [AVAudioPlayer] loaded from temp files.
 *
 * Audio bytes come from [AudioFileProvider] (= `Res.readBytes()` in composeApp),
 * which is the only reliable cross-platform way to read `composeResources/files/`.
 * The bytes are written once to the temp directory via POSIX I/O and reused
 * for the lifetime of the app session.
 *
 * SFX are preloaded eagerly on startup so the first `play()` is instant.
 * Background music is loaded on demand when [startMusic] is called.
 */
@OptIn(ExperimentalForeignApi::class)
@SingleIn(AppScope::class)
@Inject
internal class NativePlatformSoundPlayer(
    private val provider: AudioFileProvider,
    private val scope: CoroutineScope,
) : PlatformSoundPlayer {

    private val sfxCache: MutableMap<String, AVAudioPlayer> = mutableMapOf()
    private var musicPlayer: AVAudioPlayer? = null

    /** Known SFX filenames to preload eagerly at startup. */
    private val knownSfx = listOf(
        "block_place.wav",
        "line_clear_1.wav", "line_clear_2.wav",
        "line_clear_3.wav", "line_clear_4.wav",
        "voice_good.wav", "voice_great.wav",
        "voice_excellent.wav", "voice_unbelievable.wav",
        "voice_amazing.wav", "voice_combo.wav",
    )

    init {
        runCatching {
            AVAudioSession.sharedInstance()
                .setCategory(AVAudioSessionCategoryPlayback, error = null)
        }
        scope.launch(Dispatchers.Main) {
            for (filename in knownSfx) {
                val key = filename.substringBeforeLast(".")
                loadPlayer(filename)?.let { sfxCache[key] = it }
            }
        }
    }

    override fun playPlacement() = safePlay("block_place")
    override fun playClear(lines: Int) = safePlay("line_clear_${lines.coerceAtMost(4)}")
    override fun playVoiceFeedback(type: FeedbackType) = safePlay("voice_${type.name.lowercase()}")

    override fun playVoiceCombo(combo: Int) {
        val specific = "voice_combo_${combo.coerceAtMost(10)}"
        if (!safePlayReturning(specific)) {
            if (!safePlayReturning("voice_combo")) safePlay("voice_amazing")
        }
    }

    override fun startMusic() {
        if (musicPlayer?.playing == true) return
        scope.launch(Dispatchers.Main) {
            val player = loadPlayer("music_ambient.mp3") ?: return@launch
            player.numberOfLoops = -1
            player.volume = MUSIC_VOLUME
            musicPlayer = player
            player.play()
        }
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

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun safePlay(key: String) { safePlayReturning(key) }

    private fun safePlayReturning(key: String): Boolean {
        val player = sfxCache[key] ?: return false
        player.currentTime = 0.0
        return player.play()
    }

    /**
     * 1. Reads [filename] bytes from CMP resources (on Default dispatcher).
     * 2. Writes them to `NSTemporaryDirectory` via POSIX `fwrite` (reliable in K/N).
     * 3. Returns an [AVAudioPlayer] ready to play, or null on any failure.
     */
    private suspend fun loadPlayer(filename: String): AVAudioPlayer? {
        val bytes = withContext(Dispatchers.Default) {
            provider.bytes(filename)
        } ?: return null

        val url = withContext(Dispatchers.Default) {
            bytes.writeTempFile(filename)
        } ?: return null

        return runCatching { AVAudioPlayer(contentsOfURL = url, error = null) }
            .getOrNull()
            ?.also { it.prepareToPlay() }
    }

    private companion object {
        const val MUSIC_VOLUME = 0.4f
    }
}

// ── POSIX temp-file writer ────────────────────────────────────────────────────

/**
 * Writes this [ByteArray] to `{NSTemporaryDirectory}/{filename}` using POSIX I/O
 * and returns a file [NSURL] pointing at it, or null on failure.
 */
@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.writeTempFile(filename: String): NSURL? {
    val path = NSTemporaryDirectory() + filename
    val file = fopen(path, "wb") ?: return null
    try {
        usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1u, size.toULong(), file)
        }
    } finally {
        fclose(file)
    }
    return NSURL.fileURLWithPath(path)
}
