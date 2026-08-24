package ge.yet.game.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphPrivate
import ge.yet.game.feature.settings.libraries.LibrariesProvider
import ge.yet.game.miniapp.AdMobMiniAppInterstitialCapability
import ge.yet.game.miniapp.compose.MiniAppInterstitialCapability

@ContributesTo(AppScope::class)
@BindingContainer
abstract class ComposeAppBindings {
    @GraphPrivate
    @Binds
    internal abstract val ComposeLibrariesProvider.bindLibrariesProvider: LibrariesProvider

    @Binds
    internal abstract val AdMobMiniAppInterstitialCapability.bindMiniAppInterstitialCapability:
        MiniAppInterstitialCapability
}
