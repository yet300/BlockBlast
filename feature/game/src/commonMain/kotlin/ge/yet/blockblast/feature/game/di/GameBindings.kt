package ge.yet.blockblast.feature.game.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import ge.yet.blockblast.feature.game.DefaultGameComponentFactory
import ge.yet.blockblast.feature.game.GameComponent

@ContributesTo(AppScope::class)
@BindingContainer
abstract class GameBindings {
    @Binds
    internal abstract val DefaultGameComponentFactory.bindGameComponentFactory: GameComponent.Factory
}
