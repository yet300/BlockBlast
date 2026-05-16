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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
    // Tracks keys we've already tried and failed to load, so missing assets
    // don't keep paying the I/O + decode cost on every play call.
    private val sfxMisses: MutableSet<String> = mutableSetOf()
    private var musicPlayer: AVAudioPlayer? = null
    private var musicJob: Job? = null
    private var lastTrackIndex: Int = -1
    // Monotonically incremented each startMusic(); the loop captures its own
    // generation and refuses to publish/play a track if it has been superseded
    // (e.g., stopMusic ran while loadPlayer was on a background dispatcher).
    private var musicGeneration: Long = 0L

    /** Known SFX filenames to preload eagerly at startup. */
    private val knownSfx = listOf(
        "voice_good.mp3", "voice_great.mp3",
        "voice_excellent.mp3", "voice_unbelievable.mp3",
        "voice_amazing.mp3",
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
        if (musicJob?.isActive == true || musicPlayer?.playing == true) return
        val generation = ++musicGeneration
        musicJob = scope.launch(Dispatchers.Main) {
            while (true) {
                val index = MusicPlaylist.nextIndex(lastTrackIndex)
                lastTrackIndex = index
                val filename = MusicPlaylist.TRACKS[index]
                val player = loadPlayer(filename) ?: return@launch
                // After loadPlayer (which hops dispatchers), the loop may have
                // been cancelled and/or superseded by another startMusic. In
                // either case, drop this player on the floor — assigning it to
                // musicPlayer or calling play() would leak audio past stopMusic.
                coroutineContext.ensureActive()
                if (generation != musicGeneration) return@launch
                player.numberOfLoops = 0
                player.volume = MUSIC_VOLUME
                musicPlayer = player
                player.play()
                // Sleep until the track is done, then loop to the next one.
                // Add a small tail buffer to avoid an audible end-of-buffer cut.
                val durationMs = (player.duration * 1000.0).toLong().coerceAtLeast(0L)
                delay(durationMs + 50L)
            }
        }
    }

    override fun stopMusic() {
        musicGeneration++
        musicJob?.cancel()
        musicJob = null
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
        val player = sfxCache[key]
        if (player != null) {
            player.currentTime = 0.0
            return player.play()
        }
        // Lazy-load on miss: matches Android behaviour and avoids the
        // requirement to enumerate every SFX in [knownSfx]. The current call
        // can't play (load is async), but subsequent calls will hit the cache.
        if (key !in sfxMisses) {
            scope.launch(Dispatchers.Main) {
                val loaded = loadPlayer("$key.mp3")
                if (loaded != null) sfxCache[key] = loaded else sfxMisses += key
            }
        }
        return false
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
