package ge.yet.game.blockblast.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import ge.yet.game.blockblast.data.audio.BlockBlastPlatformAudioPlayer
import ge.yet.game.blockblast.data.audio.NativeBlockBlastPlatformAudioPlayer
import ge.yet.game.miniapp.metro.MiniAppSessionScope

@ContributesTo(MiniAppSessionScope::class)
@BindingContainer
internal abstract class NativeBlockBlastAudioBindings {
    @Binds
    internal abstract val NativeBlockBlastPlatformAudioPlayer.bindBlockBlastPlatformAudioPlayer:
        BlockBlastPlatformAudioPlayer
}
