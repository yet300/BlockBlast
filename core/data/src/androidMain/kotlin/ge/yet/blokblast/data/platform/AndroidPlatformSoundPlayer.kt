package ge.yet.blokblast.data.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.domain.model.FeedbackType

/**
 * Android actual — uses [SoundPool] for low-latency SFX and [MediaPlayer] for
 * looping background music.
 *
 * Two correctness fixes vs. the naive impl:
 *   1. SoundPool readiness — `pool.load()` is async and `pool.play()` against a
 *      not-yet-ready sample silently drops with `play soundID N not READY`.
 *      We register an `OnLoadCompleteListener` and only `pool.play()` IDs we
 *      have seen complete loading. Plays before-load are dropped silently
 *      rather than producing a warning.
 *   2. Music re-entrancy — `MediaPlayer.isPlaying` returns false while the
 *      player is in PREPARING state. A second `startMusic()` call during
 *      preparation would `release()` the in-flight player, the old
 *      `OnPreparedListener` would then fire `start()` on the released player
 *      and the audio stack would tear the stream down 30–40 ms later. We
 *      track an explicit `musicState` (IDLE / PREPARING / PLAYING) and only
 *      build a new player when we are actually idle.
 */
@SingleIn(AppScope::class)
@Inject
internal class AndroidPlatformSoundPlayer(
    private val ctx: Context,
) : PlatformSoundPlayer {

    private val audioAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(audioAttrs)
        .build()

    /** SoundPool IDs keyed by resource name (0 = failed / not found). */
    private val ids: MutableMap<String, Int> = mutableMapOf()

    /** IDs that have completed loading and are safe to play. */
    private val readyIds: MutableSet<Int> = mutableSetOf()

    private enum class MusicState { IDLE, PREPARING, PLAYING }

    private var musicPlayer: MediaPlayer? = null
    private var musicState: MusicState = MusicState.IDLE
    private var lastTrackIndex: Int = -1

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) readyIds += sampleId
        }
    }

    override fun playPlacement() = safePlay("block_place")

    override fun playClear(lines: Int) = safePlay("line_clear_${lines.coerceAtMost(4)}")

    override fun playVoiceFeedback(type: FeedbackType) =
        safePlay("voice_${type.name.lowercase()}")

    override fun playVoiceCombo(combo: Int) {
        val specific = "voice_combo_${combo.coerceAtMost(10)}"
        val id = ids.getOrPut(specific) { resolve(specific) }
        if (id != 0 && id in readyIds) {
            pool.play(id, 1f, 1f, 1, 0, 1f)
        } else if (id == 0 || id !in readyIds) {
            // Specific combo file missing or still loading — fall back.
            safePlay("voice_amazing")
        }
    }

    override fun startMusic() {
        // Re-entrancy guard. PREPARING means a previous startMusic is in flight;
        // do not release it from under its OnPreparedListener.
        if (musicState != MusicState.IDLE) return
        playTrack(MusicPlaylist.nextIndex(lastTrackIndex))
    }

    private fun playTrack(index: Int) {
        lastTrackIndex = index
        val filename = MusicPlaylist.TRACKS[index]
        runCatching {
            val afd = ctx.assets.openFd("${AUDIO_DIR}$filename")
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
                setOnPreparedListener {
                    if (musicState == MusicState.PREPARING) {
                        musicState = MusicState.PLAYING
                        it.start()
                    } else {
                        runCatching { it.release() }
                    }
                }
                setOnCompletionListener { mp ->
                    runCatching { mp.release() }
                    if (musicPlayer === mp && musicState == MusicState.PLAYING) {
                        musicPlayer = null
                        musicState = MusicState.IDLE
                        playTrack(MusicPlaylist.nextIndex(lastTrackIndex))
                    }
                }
                setOnErrorListener { mp, _, _ ->
                    runCatching { mp.release() }
                    if (musicPlayer === mp) {
                        musicPlayer = null
                        musicState = MusicState.IDLE
                    }
                    true
                }
            }
            musicPlayer = player
            musicState = MusicState.PREPARING
            player.prepareAsync()
        }.onFailure {
            musicPlayer = null
            musicState = MusicState.IDLE
        }
    }

    override fun stopMusic() {
        val player = musicPlayer ?: return
        when (musicState) {
            MusicState.PLAYING -> runCatching { player.stop() }
            MusicState.PREPARING -> {
                // Don't call stop() on a preparing player — that's an
                // IllegalStateException. The OnPreparedListener will see the
                // state has flipped and tear down for us. Mark idle now so a
                // racing startMusic() will create a fresh player after the
                // old one finishes preparing and disposes itself.
            }
            MusicState.IDLE -> Unit
        }
        if (musicState != MusicState.PREPARING) {
            runCatching { player.release() }
            musicPlayer = null
        }
        musicState = MusicState.IDLE
    }

    override fun release() {
        stopMusic()
        pool.release()
    }

    private fun safePlay(resName: String) {
        val id = ids.getOrPut(resName) { resolve(resName) }
        if (id != 0 && id in readyIds) {
            pool.play(id, 1f, 1f, 1, 0, 1f)
        }
        // else: still loading — silently drop. The next call will succeed.
    }

    /**
     * Opens the asset as an [android.content.res.AssetFileDescriptor] and
     * registers it with [SoundPool]. Tries `.wav` then `.mp3`; returns 0 if
     * not found. The returned ID is *not* immediately playable — the
     * [SoundPool.OnLoadCompleteListener] decides that.
     */
    private fun resolve(resName: String): Int {
        for (ext in AUDIO_EXTENSIONS) {
            val path = "$AUDIO_DIR$resName.$ext"
            runCatching {
                val afd = ctx.assets.openFd(path)
                val id = pool.load(afd, 1)
                afd.close()
                if (id != 0) return id
            }
        }
        return 0
    }

    private companion object {
        const val MAX_STREAMS = 6
        const val MUSIC_VOLUME = 0.4f
        private val AUDIO_EXTENSIONS = arrayOf("wav", "mp3")

        const val AUDIO_DIR =
            "composeResources/blockblast.composeapp.generated.resources/files/audio/"
    }
}
