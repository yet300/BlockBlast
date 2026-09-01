package ge.yet.game.fruitmerge

import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.game.fruitmerge.audio.FruitMergeAudioAdapter
import ge.yet.game.fruitmerge.engine.FruitMergeEngine
import ge.yet.game.fruitmerge.engine.FruitMergeRules
import ge.yet.game.fruitmerge.persistence.FruitMergePersistence
import ge.yet.game.fruitmerge.session.DefaultFruitMergeSessionComponent
import ge.yet.game.fruitmerge.session.FruitMergeSessionComponent
import ge.yet.game.fruitmerge.store.FruitMergeStoreFactory
import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.compose.MiniAppInterstitialCapability
import ge.yet.game.miniapp.metro.MiniAppSessionScope

@ContributesTo(MiniAppSessionScope::class)
@BindingContainer
abstract class FruitMergeSessionBindings {
    companion object {
        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideRules(): FruitMergeRules = FruitMergeEngine()

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun providePersistence(storage: MiniAppStorage): FruitMergePersistence =
            FruitMergePersistence(storage)

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideAudioAdapter(audio: MiniAppAudio): FruitMergeAudioAdapter =
            FruitMergeAudioAdapter(audio)

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideSessionComponent(
            componentContext: ComponentContext,
            storeFactory: FruitMergeStoreFactory,
            persistence: FruitMergePersistence,
            visibility: MiniAppVisibilitySource,
            audio: FruitMergeAudioAdapter,
        ): FruitMergeSessionComponent = DefaultFruitMergeSessionComponent(
            componentContext = componentContext,
            storeFactory = storeFactory,
            persistence = persistence,
            visibility = visibility,
            audio = audio,
        )

        @Provides
        @SingleIn(MiniAppSessionScope::class)
        internal fun provideSession(
            component: FruitMergeSessionComponent,
            interstitials: MiniAppInterstitialCapability,
        ): FruitMergeSession = FruitMergeSession(component, interstitials)
    }
}
