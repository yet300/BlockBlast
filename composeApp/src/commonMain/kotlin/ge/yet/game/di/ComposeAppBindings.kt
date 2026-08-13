package ge.yet.game.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import ge.yet.game.feature.settings.libraries.LibrariesProvider

@ContributesTo(AppScope::class)
@BindingContainer
abstract class ComposeAppBindings {
    @Binds
    internal abstract val ComposeLibrariesProvider.bindLibrariesProvider: LibrariesProvider
}
