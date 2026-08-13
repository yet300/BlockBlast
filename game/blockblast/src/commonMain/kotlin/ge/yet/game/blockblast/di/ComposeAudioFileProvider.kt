package ge.yet.game.blockblast.di

import ge.yet.game.blockblast.generated.resources.Res
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.domain.repository.AudioFileProvider

/**
 * Reads audio files from `composeResources/files/audio/` via CMP's [Res.readBytes].
 * This is the only correct cross-platform way to access those assets on iOS —
 * direct `NSBundle` or asset-path hacks are brittle and break silently.
 */
@SingleIn(AppScope::class)
@Inject
internal class ComposeAudioFileProvider : AudioFileProvider {
    override fun path(filename: String): String =
        "composeResources/ge.yet.game.blockblast.generated.resources/files/audio/$filename"

    override suspend fun bytes(filename: String): ByteArray? = runCatching {
        Res.readBytes("files/audio/$filename")
    }.getOrNull()
}
