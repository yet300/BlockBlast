package ge.yet.blockblast.feature.game.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import ge.yet.blockblast.feature.game.DefaultGameComponentFactory
import ge.yet.blockblast.feature.game.GameComponent

@ContributesTo(AppScope::class)
@BindingContainer
object GameBindings {
    @Provides
    internal fun provideGameComponentFactory(
        impl: DefaultGameComponentFactory,
    ): GameComponent.Factory = impl
}
