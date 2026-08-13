package ge.yet.game.blockblast.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.game.blockblast.component.game.DefaultGameComponentFactory
import ge.yet.game.blockblast.component.game.GameComponent
import ge.yet.game.blockblast.component.result.DefaultGameResultComponentFactory
import ge.yet.game.blockblast.component.result.GameResultComponent
import ge.yet.game.blockblast.data.repository.SettingsBackedBlockBlastTutorialRepository
import ge.yet.game.blockblast.data.repository.SettingsBackedGameSaveRepository
import ge.yet.game.blockblast.domain.engine.GameSessionReducer
import ge.yet.game.blockblast.domain.engine.ScoreCalculator
import ge.yet.game.blockblast.domain.engine.ShapeGenerator
import ge.yet.game.blockblast.domain.repository.BlockBlastTutorialRepository
import ge.yet.game.blockblast.domain.repository.GameSaveRepository
import ge.yet.game.domain.api.GameSaveApi
import ge.yet.game.domain.repository.AudioFileProvider

@ContributesTo(AppScope::class)
@BindingContainer
abstract class BlockBlastBindings {
    @Binds
    internal abstract val SettingsBackedGameSaveRepository.bindGameSaveRepository: GameSaveRepository

    @Binds
    internal abstract val SettingsBackedGameSaveRepository.bindGameSaveApi: GameSaveApi

    @Binds
    internal abstract val SettingsBackedBlockBlastTutorialRepository.bindBlockBlastTutorialRepository:
        BlockBlastTutorialRepository

    @Binds
    internal abstract val ComposeAudioFileProvider.bindAudioFileProvider: AudioFileProvider

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
