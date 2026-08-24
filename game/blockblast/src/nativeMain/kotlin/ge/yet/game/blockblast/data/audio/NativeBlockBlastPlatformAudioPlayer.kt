package ge.yet.game.blockblast.data.audio

import com.app.common.decompose.coroutineScope
import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.blockblast.di.ComposeAudioFileProvider
import ge.yet.game.miniapp.metro.MiniAppSessionScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
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

@OptIn(ExperimentalForeignApi::class)
@SingleIn(MiniAppSessionScope::class)
@Inject
internal class NativeBlockBlastPlatformAudioPlayer(
    private val provider: ComposeAudioFileProvider,
    componentContext: ComponentContext,
) : BlockBlastPlatformAudioPlayer {

    private val scope = componentContext.coroutineScope()
    private val voiceCache = mutableMapOf<String, AVAudioPlayer>()
    private val voiceMisses = mutableSetOf<String>()
    private var musicPlayer: AVAudioPlayer? = null
    private var voicePlayer: AVAudioPlayer? = null
    private var musicJob: Job? = null
    private var lastTrackIndex: Int = -1
    private var musicGeneration: Long = 0L

    init {
        runCatching {
            AVAudioSession.sharedInstance()
                .setCategory(AVAudioSessionCategoryPlayback, error = null)
        }
    }

    override fun playVoice(filename: String) {
        voicePlayer?.stop()
        voicePlayer = null
        val cached = voiceCache[filename]
        if (cached != null) {
            cached.currentTime = 0.0
            if (cached.play()) voicePlayer = cached
            return
        }
        if (filename in voiceMisses) return

        scope.launch(Dispatchers.Main) {
            val loaded = loadPlayer(filename)
            coroutineContext.ensureActive()
            if (loaded != null) {
                voiceCache[filename] = loaded
                loaded.currentTime = 0.0
                if (loaded.play()) voicePlayer = loaded
            } else {
                voiceMisses += filename
            }
        }
    }

    override fun startMusic(tracks: List<String>) {
        if (tracks.isEmpty()) return
        if (musicJob?.isActive == true || musicPlayer?.playing == true) return
        val playlist = tracks.toList()
        val generation = ++musicGeneration
        musicJob = scope.launch(Dispatchers.Main) {
            while (true) {
                val index = nextTrackIndex(playlist.size, lastTrackIndex)
                lastTrackIndex = index
                val player = loadPlayer(playlist[index]) ?: return@launch
                coroutineContext.ensureActive()
                if (generation != musicGeneration) return@launch
                player.numberOfLoops = 0
                player.volume = MUSIC_VOLUME
                musicPlayer = player
                player.play()
                val durationMs = (player.duration * 1_000.0).toLong().coerceAtLeast(0L)
                delay(durationMs + TRACK_TAIL_MILLIS)
            }
        }
    }

    override fun stopMusic() {
        musicGeneration += 1
        musicJob?.cancel()
        musicJob = null
        musicPlayer?.stop()
        musicPlayer = null
    }

    override fun release() {
        stopMusic()
        voicePlayer?.stop()
        voicePlayer = null
        voiceCache.values.forEach { it.stop() }
        voiceCache.clear()
        voiceMisses.clear()
    }

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
        const val TRACK_TAIL_MILLIS = 50L
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.writeTempFile(filename: String): NSURL? {
    if (isEmpty()) return null
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
