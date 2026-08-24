package ge.yet.game.miniapp.audio.di

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphPrivate
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.game.miniapp.audio.internal.AndroidAudioSink
import ge.yet.game.miniapp.audio.internal.DefaultAndroidAudioPlatform
import ge.yet.game.miniapp.audio.internal.PlatformAudioSink

@ContributesTo(
    scope = AppScope::class,
    replaces = [UnavailableMiniAppAudioSinkBindings::class],
)
@BindingContainer
object AndroidMiniAppAudioBindings {
    @Provides
    @SingleIn(AppScope::class)
    @GraphPrivate
    internal fun providePlatformAudioSink(context: Context): PlatformAudioSink =
        AndroidAudioSink(DefaultAndroidAudioPlatform(context))
}
