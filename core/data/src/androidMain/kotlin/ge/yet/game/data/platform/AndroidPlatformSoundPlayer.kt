package ge.yet.game.data.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.domain.repository.AudioFileProvider

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
    private val provider: AudioFileProvider,
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
    private var musicTracks: List<String> = emptyList()
    private var voiceStreamId: Int = 0

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) readyIds += sampleId
        }
    }

    override fun playSound(filename: String) {
        if (voiceStreamId != 0) pool.stop(voiceStreamId)
        voiceStreamId = safePlay(filename)
    }

    override fun startMusic(tracks: List<String>) {
        if (tracks.isEmpty()) return
        // Re-entrancy guard. PREPARING means a previous startMusic is in flight;
        // do not release it from under its OnPreparedListener.
        if (musicState != MusicState.IDLE) return
        musicTracks = tracks.toList()
        playTrack(nextTrackIndex(musicTracks.size, lastTrackIndex))
    }

    private fun playTrack(index: Int) {
        lastTrackIndex = index
        val filename = musicTracks[index]
        runCatching {
            val afd = ctx.assets.openFd(provider.path(filename))
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
                        playTrack(nextTrackIndex(musicTracks.size, lastTrackIndex))
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

    private fun safePlay(filename: String): Int {
        val id = ids.getOrPut(filename) { resolve(filename) }
        if (id != 0 && id in readyIds) {
            return pool.play(id, 1f, 1f, 1, 0, 1f)
        }
        // else: still loading — silently drop. The next call will succeed.
        return 0
    }

    /**
     * Opens the asset as an [android.content.res.AssetFileDescriptor] and
     * registers it with [SoundPool]. Returns 0 if it is not found. The
     * returned ID is *not* immediately playable — the
     * [SoundPool.OnLoadCompleteListener] decides that.
     */
    private fun resolve(filename: String): Int = runCatching {
        val afd = ctx.assets.openFd(provider.path(filename))
        val id = pool.load(afd, 1)
        afd.close()
        id
    }.getOrDefault(0)

    private companion object {
        const val MAX_STREAMS = 6
        const val MUSIC_VOLUME = 0.4f
    }
}
