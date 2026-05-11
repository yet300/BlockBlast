package ge.yet.blockblast.feature.home.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import ge.yet.blockblast.feature.home.DefaultHomeComponentFactory
import ge.yet.blockblast.feature.home.HomeComponent

@ContributesTo(AppScope::class)
@BindingContainer
abstract class HomeBindings {
    @Binds
    internal abstract val DefaultHomeComponentFactory.bindHomeComponentFactory: HomeComponent.Factory
}
