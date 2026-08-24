package ge.yet.game.blockblast.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.blockblast.di.ComposeAudioFileProvider
import ge.yet.game.miniapp.metro.MiniAppSessionScope

@SingleIn(MiniAppSessionScope::class)
@Inject
internal class AndroidBlockBlastPlatformAudioPlayer(
    private val context: Context,
    private val provider: ComposeAudioFileProvider,
) : BlockBlastPlatformAudioPlayer {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids = mutableMapOf<String, Int>()
    private val readyIds = mutableSetOf<Int>()
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

    override fun playVoice(filename: String) {
        if (voiceStreamId != 0) pool.stop(voiceStreamId)
        voiceStreamId = safePlay(filename)
    }

    override fun startMusic(tracks: List<String>) {
        if (tracks.isEmpty() || musicState != MusicState.IDLE) return
        musicTracks = tracks.toList()
        playTrack(nextTrackIndex(musicTracks.size, lastTrackIndex))
    }

    override fun stopMusic() {
        val player = musicPlayer ?: return
        when (musicState) {
            MusicState.PLAYING -> runCatching { player.stop() }
            MusicState.PREPARING,
            MusicState.IDLE,
            -> Unit
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

    private fun playTrack(index: Int) {
        lastTrackIndex = index
        val filename = musicTracks[index]
        runCatching {
            val descriptor = context.assets.openFd(provider.path(filename))
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                descriptor.close()
                setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
                setOnPreparedListener { prepared ->
                    if (musicPlayer === prepared && musicState == MusicState.PREPARING) {
                        musicState = MusicState.PLAYING
                        prepared.start()
                    } else {
                        runCatching { prepared.release() }
                    }
                }
                setOnCompletionListener { completed ->
                    runCatching { completed.release() }
                    if (musicPlayer === completed && musicState == MusicState.PLAYING) {
                        musicPlayer = null
                        musicState = MusicState.IDLE
                        playTrack(nextTrackIndex(musicTracks.size, lastTrackIndex))
                    }
                }
                setOnErrorListener { failed, _, _ ->
                    runCatching { failed.release() }
                    if (musicPlayer === failed) {
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

    private fun safePlay(filename: String): Int {
        val id = ids.getOrPut(filename) { resolve(filename) }
        return if (id != 0 && id in readyIds) {
            pool.play(id, 1f, 1f, 1, 0, 1f)
        } else {
            0
        }
    }

    private fun resolve(filename: String): Int = runCatching {
        val descriptor = context.assets.openFd(provider.path(filename))
        val id = pool.load(descriptor, 1)
        descriptor.close()
        id
    }.getOrDefault(0)

    private enum class MusicState {
        IDLE,
        PREPARING,
        PLAYING,
    }

    private companion object {
        const val MAX_STREAMS = 6
        const val MUSIC_VOLUME = 0.4f
    }
}
