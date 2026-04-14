package ge.yet.blokblast.data.platform

import android.content.Contextimport android.media.AudioAttributes
import android.media.SoundPool
import ge.yet.blokblast.domain.model.FeedbackType

/**
 * Android actual — uses [SoundPool] for low-latency SFX. Asset files are expected
 * at `res/raw/block_place.ogg`, `res/raw/line_clear.ogg`, etc. Missing assets are
 * silently ignored so the engine never crashes in dev.
 *
 * `internal` — invisible to composeApp/feature modules.
 */
internal class AndroidPlatformSoundPlayer(
    private val ctx: Context,
) : PlatformSoundPlayer {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    // Resource ids are resolved lazily by name so `:shared` doesn't require
    // a fixed R file — host app drops the matching assets in res/raw/.
    private val ids: MutableMap<String, Int> = mutableMapOf()

    override fun playPlacement() = safePlay("block_place")
    override fun playClear(lines: Int) = safePlay("line_clear_${lines.coerceAtMost(4)}")
    override fun playVoiceFeedback(type: FeedbackType) = safePlay("voice_${type.name.lowercase()}")
    override fun playVoiceCombo(combo: Int) = safePlay("voice_combo_${combo.coerceAtMost(10)}")

    override fun release() {
        pool.release()
    }

    private fun safePlay(resName: String) {
        val id = ids.getOrPut(resName) { resolve(resName) }
        if (id != 0) pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    private fun resolve(resName: String): Int = runCatching {
        val rawId = ctx.resources.getIdentifier(resName, "raw", ctx.packageName)
        if (rawId == 0) 0 else pool.load(ctx, rawId, 1)
    }.getOrDefault(0)

    private companion object {
        const val MAX_STREAMS = 6
    }
}
