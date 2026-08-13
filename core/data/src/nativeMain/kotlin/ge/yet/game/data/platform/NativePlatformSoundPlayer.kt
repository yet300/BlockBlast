package ge.yet.game.data.platform

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.domain.repository.AudioFileProvider
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
 * Audio bytes come from the active game's [AudioFileProvider], which can use
 * Compose Resources to read `composeResources/files/` portably.
 * The bytes are written once to the temp directory via POSIX I/O and reused
 * for the lifetime of the app session.
 *
 * Sounds and background music are loaded on demand.
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
    private var voicePlayer: AVAudioPlayer? = null
    private var musicJob: Job? = null
    private var lastTrackIndex: Int = -1
    // Monotonically incremented each startMusic(); the loop captures its own
    // generation and refuses to publish/play a track if it has been superseded
    // (e.g., stopMusic ran while loadPlayer was on a background dispatcher).
    private var musicGeneration: Long = 0L

    init {
        runCatching {
            AVAudioSession.sharedInstance()
                .setCategory(AVAudioSessionCategoryPlayback, error = null)
        }
    }

    override fun playSound(filename: String) = playVoice(filename)

    override fun startMusic(tracks: List<String>) {
        if (tracks.isEmpty()) return
        if (musicJob?.isActive == true || musicPlayer?.playing == true) return
        val playlist = tracks.toList()
        val generation = ++musicGeneration
        musicJob = scope.launch(Dispatchers.Main) {
            while (true) {
                val index = nextTrackIndex(playlist.size, lastTrackIndex)
                lastTrackIndex = index
                val filename = playlist[index]
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
        voicePlayer?.stop()
        voicePlayer = null
        sfxCache.values.forEach { it.stop() }
        sfxCache.clear()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun playVoice(filename: String) {
        voicePlayer?.stop()
        voicePlayer = null
        val player = sfxCache[filename]
        if (player != null) {
            player.currentTime = 0.0
            if (player.play()) voicePlayer = player
            return
        }
        if (filename !in sfxMisses) {
            scope.launch(Dispatchers.Main) {
                val loaded = loadPlayer(filename)
                if (loaded != null) {
                    sfxCache[filename] = loaded
                    loaded.currentTime = 0.0
                    if (loaded.play()) voicePlayer = loaded
                } else {
                    sfxMisses += filename
                }
            }
        }
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
