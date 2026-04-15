package ge.yet.blockblast.feature.home.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.blockblast.feature.home.DefaultHomeComponentFactory
import ge.yet.blockblast.feature.home.HomeComponent

@ContributesTo(AppScope::class)
@BindingContainer
object HomeBindings {
    @Provides
    @SingleIn(AppScope::class)
    internal fun provideHomeComponentFactory(
        impl: DefaultHomeComponentFactory,
    ): HomeComponent.Factory = impl
}
