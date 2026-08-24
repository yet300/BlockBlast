package ge.yet.game.miniapp.audio.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphPrivate
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.game.miniapp.audio.internal.DefaultIosAudioPlatform
import ge.yet.game.miniapp.audio.internal.IosAudioSink
import ge.yet.game.miniapp.audio.internal.PlatformAudioSink

@ContributesTo(
    scope = AppScope::class,
    replaces = [UnavailableMiniAppAudioSinkBindings::class],
)
@BindingContainer
object IosMiniAppAudioBindings {
    @Provides
    @SingleIn(AppScope::class)
    @GraphPrivate
    internal fun providePlatformAudioSink(): PlatformAudioSink =
        IosAudioSink(DefaultIosAudioPlatform())
}
