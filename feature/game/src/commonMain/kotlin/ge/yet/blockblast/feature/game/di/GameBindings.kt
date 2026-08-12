package ge.yet.blockblast.feature.game.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.blockblast.feature.game.DefaultGameComponentFactory
import ge.yet.blockblast.feature.game.GameComponent
import ge.yet.blockblast.feature.game.result.DefaultGameResultComponentFactory
import ge.yet.blockblast.feature.game.result.GameResultComponent
import ge.yet.game.domain.engine.GameSessionReducer
import ge.yet.game.domain.engine.ScoreCalculator
import ge.yet.game.domain.engine.ShapeGenerator

@ContributesTo(AppScope::class)
@BindingContainer
abstract class GameBindings {
    @Binds
    internal abstract val DefaultGameComponentFactory.bindGameComponentFactory: GameComponent.Factory

    @Binds
    internal abstract val DefaultGameResultComponentFactory.bindGameResultComponentFactory:
        GameResultComponent.Factory

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        internal fun provideShapeGenerator(): ShapeGenerator = ShapeGenerator.default()

        @Provides
        internal fun provideScoreCalculator(): ScoreCalculator = ScoreCalculator()

        @Provides
        internal fun provideGameSessionReducer(
            shapeGenerator: ShapeGenerator,
            scoreCalculator: ScoreCalculator,
        ): GameSessionReducer = GameSessionReducer(shapeGenerator, scoreCalculator)
    }
}
