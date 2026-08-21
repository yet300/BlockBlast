package ge.yet.game.blockblast.di

import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.game.blockblast.component.game.DefaultGameComponentFactory
import ge.yet.game.blockblast.component.game.GameComponent
import ge.yet.game.blockblast.component.game.store.GameStoreFactory
import ge.yet.game.blockblast.component.result.DefaultGameResultComponentFactory
import ge.yet.game.blockblast.component.result.GameResultComponent
import ge.yet.game.blockblast.domain.engine.GameSessionReducer
import ge.yet.game.blockblast.domain.engine.ScoreCalculator
import ge.yet.game.blockblast.domain.engine.ShapeGenerator
import ge.yet.game.blockblast.domain.repository.BlockBlastTutorialRepository
import ge.yet.game.blockblast.session.BlockBlastSession
import ge.yet.game.blockblast.session.BlockBlastSessionComponent
import ge.yet.game.blockblast.session.DefaultBlockBlastSessionComponentFactory
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.domain.repository.AudioRepository
import ge.yet.game.domain.repository.FeedbackPreferences
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.compose.MiniAppInterstitialCapability
import ge.yet.game.miniapp.compose.MiniAppSession
import ge.yet.game.miniapp.metro.MiniAppSessionScope

@ContributesTo(MiniAppSessionScope::class)
@BindingContainer
abstract class BlockBlastSessionBindings {
    companion object {
        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideShapeGenerator(): ShapeGenerator = ShapeGenerator.default()

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideScoreCalculator(): ScoreCalculator = ScoreCalculator()

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideGameSessionReducer(
            generator: ShapeGenerator,
            calculator: ScoreCalculator,
        ): GameSessionReducer = GameSessionReducer(generator, calculator)

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideGameComponentFactory(
            gameStoreFactory: GameStoreFactory,
            audio: AudioRepository,
            tutorial: BlockBlastTutorialRepository,
            analytics: AnalyticRepository,
            visibility: MiniAppVisibilitySource,
        ): GameComponent.Factory = DefaultGameComponentFactory(
            gameStoreFactory = gameStoreFactory,
            audio = audio,
            tutorialRepository = tutorial,
            analytics = analytics,
            visibility = visibility,
        )

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideGameResultComponentFactory(
            visibility: MiniAppVisibilitySource,
        ): GameResultComponent.Factory = DefaultGameResultComponentFactory(visibility)

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideBlockBlastSessionComponentFactory(
            gameFactory: GameComponent.Factory,
            resultFactory: GameResultComponent.Factory,
        ): BlockBlastSessionComponent.Factory = DefaultBlockBlastSessionComponentFactory(
            gameFactory = gameFactory,
            resultFactory = resultFactory,
        )

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideBlockBlastSessionComponent(
            factory: BlockBlastSessionComponent.Factory,
            componentContext: ComponentContext,
            visibility: MiniAppVisibilitySource,
            host: MiniAppSessionHost,
        ): BlockBlastSessionComponent = factory.create(componentContext, visibility, host)

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        @Named("game.blockblast.session")
        internal fun provideMiniAppSession(
            component: BlockBlastSessionComponent,
            interstitials: MiniAppInterstitialCapability,
            feedback: FeedbackPreferences,
        ): MiniAppSession = BlockBlastSession(component, interstitials, feedback)
    }
}
