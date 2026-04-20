package ge.yet.blockblast.feature.root.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import ge.yet.blockblast.feature.root.DefaultRootComponentFactory
import ge.yet.blockblast.feature.root.RootComponent

@ContributesTo(AppScope::class)
@BindingContainer
object RootBindings {
    @Provides
    internal fun provideRootComponentFactory(
        impl: DefaultRootComponentFactory,
    ): RootComponent.Factory = impl
}
