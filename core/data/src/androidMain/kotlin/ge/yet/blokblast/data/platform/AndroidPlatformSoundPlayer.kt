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
 * Audio files live in a single place: `composeApp/src/commonMain/composeResources/files/audio/`.
 * Compose Multiplatform packages those files into `assets/composeResources/
 * blockblast.composeapp.generated.resources/files/audio/` in the APK, so they
 * are accessed via [Context.getAssets] without any duplication across modules.
 *
 * Missing assets are silently ignored — the engine never crashes in dev.
 * `internal` — invisible to composeApp/feature modules.
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
    private var musicPlayer: MediaPlayer? = null

    override fun playPlacement() = safePlay("block_place")

    override fun playClear(lines: Int) = safePlay("line_clear_${lines.coerceAtMost(4)}")

    override fun playVoiceFeedback(type: FeedbackType) =
        safePlay("voice_${type.name.lowercase()}")

    override fun playVoiceCombo(combo: Int) {
        // Try specific combo file first (voice_combo_1…10), fall back to voice_amazing
        val specific = "voice_combo_${combo.coerceAtMost(10)}"
        val id = ids.getOrPut(specific) { resolve(specific) }
        if (id != 0) {
            pool.play(id, 1f, 1f, 1, 0, 1f)
        } else {
            safePlay("voice_amazing")
        }
    }

    override fun startMusic() {
        if (musicPlayer?.isPlaying == true) return
        runCatching {
            val afd = ctx.assets.openFd("${AUDIO_DIR}music_ambient.mp3")
            musicPlayer?.release()
            musicPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
                prepare()
                start()
            }
        }
    }

    override fun stopMusic() {
        runCatching {
            musicPlayer?.stop()
            musicPlayer?.release()
        }
        musicPlayer = null
    }

    override fun release() {
        stopMusic()
        pool.release()
    }

    private fun safePlay(resName: String) {
        val id = ids.getOrPut(resName) { resolve(resName) }
        if (id != 0) pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    /**
     * Opens the asset as an [AssetFileDescriptor] and loads it into [SoundPool].
     * Tries `.wav` then `.mp3`; returns 0 if not found.
     */
    private fun resolve(resName: String): Int {
        for (ext in listOf("wav", "mp3")) {
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

        /**
         * CMP packages `composeResources/files/` into the APK assets under this prefix.
         * Derived from the generated [blockblast.composeapp.generated.resources.Res] class.
         */
        const val AUDIO_DIR =
            "composeResources/blockblast.composeapp.generated.resources/files/audio/"
    }
}
