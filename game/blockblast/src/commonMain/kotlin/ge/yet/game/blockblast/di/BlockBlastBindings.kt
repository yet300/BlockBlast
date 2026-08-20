package ge.yet.game.blockblast.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphPrivate
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.game.blockblast.component.game.DefaultGameComponentFactory
import ge.yet.game.blockblast.component.game.GameComponent
import ge.yet.game.blockblast.component.result.DefaultGameResultComponentFactory
import ge.yet.game.blockblast.component.result.GameResultComponent
import ge.yet.game.blockblast.data.repository.SettingsBackedBlockBlastTutorialRepository
import ge.yet.game.blockblast.data.repository.SettingsBackedBestScoreRepository
import ge.yet.game.blockblast.data.repository.SettingsBackedGameSaveRepository
import ge.yet.game.blockblast.domain.engine.GameSessionReducer
import ge.yet.game.blockblast.domain.engine.ScoreCalculator
import ge.yet.game.blockblast.domain.engine.ShapeGenerator
import ge.yet.game.blockblast.domain.repository.BlockBlastTutorialRepository
import ge.yet.game.blockblast.domain.repository.BestScoreRepository
import ge.yet.game.blockblast.domain.repository.GameSaveRepository
import ge.yet.game.domain.api.GameSaveApi
import ge.yet.game.domain.repository.AudioFileProvider
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@ContributesTo(AppScope::class)
@BindingContainer
abstract class BlockBlastBindings {
    @Binds
    internal abstract val SettingsBackedBestScoreRepository.bindBestScoreRepository:
        BestScoreRepository

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
        @GraphPrivate
        @Provides
        @SingleIn(AppScope::class)
        internal fun provideLegacyActiveMiniAppVisibilitySource(): MiniAppVisibilitySource =
            object : MiniAppVisibilitySource {
                override val visibility =
                    MutableStateFlow(MiniAppVisibility.ACTIVE).asStateFlow()
            }

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
