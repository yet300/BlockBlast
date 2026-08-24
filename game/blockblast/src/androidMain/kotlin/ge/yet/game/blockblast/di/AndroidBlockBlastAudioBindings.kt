package ge.yet.game.blockblast.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import ge.yet.game.blockblast.data.audio.AndroidBlockBlastPlatformAudioPlayer
import ge.yet.game.blockblast.data.audio.BlockBlastPlatformAudioPlayer
import ge.yet.game.miniapp.metro.MiniAppSessionScope

@ContributesTo(MiniAppSessionScope::class)
@BindingContainer
internal abstract class AndroidBlockBlastAudioBindings {
    @Binds
    internal abstract val AndroidBlockBlastPlatformAudioPlayer.bindBlockBlastPlatformAudioPlayer:
        BlockBlastPlatformAudioPlayer
}
