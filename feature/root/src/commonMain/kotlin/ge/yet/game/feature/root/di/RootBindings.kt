package ge.yet.game.feature.root.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import ge.yet.game.feature.root.DefaultRootComponentFactory
import ge.yet.game.feature.root.RootComponent

@ContributesTo(AppScope::class)
@BindingContainer
abstract class RootBindings {
    @Binds
    internal abstract val DefaultRootComponentFactory.bindRootComponentFactory: RootComponent.Factory
}
