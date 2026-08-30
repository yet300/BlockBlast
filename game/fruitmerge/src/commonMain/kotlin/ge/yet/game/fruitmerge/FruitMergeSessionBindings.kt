package ge.yet.game.fruitmerge

import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.game.fruitmerge.engine.FruitMergeEngine
import ge.yet.game.fruitmerge.engine.FruitMergeRules
import ge.yet.game.fruitmerge.persistence.FruitMergePersistence
import ge.yet.game.fruitmerge.session.DefaultFruitMergeComponent
import ge.yet.game.fruitmerge.session.FruitMergeComponent
import ge.yet.game.fruitmerge.store.FruitMergeStoreFactory
import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.compose.MiniAppInterstitialCapability
import ge.yet.game.miniapp.metro.MiniAppSessionScope

@ContributesTo(MiniAppSessionScope::class)
@BindingContainer
internal object FruitMergeSessionBindings {
    @Provides
    @SingleIn(MiniAppSessionScope::class)
    fun provideRules(): FruitMergeRules = FruitMergeEngine()

    @Provides
    @SingleIn(MiniAppSessionScope::class)
    fun providePersistence(storage: MiniAppStorage): FruitMergePersistence = FruitMergePersistence(storage)

    @Provides
    @SingleIn(MiniAppSessionScope::class)
    fun provideComponent(
        componentContext: ComponentContext,
        storeFactory: FruitMergeStoreFactory,
        visibility: MiniAppVisibilitySource,
    ): FruitMergeComponent = DefaultFruitMergeComponent(componentContext, storeFactory, visibility)

    @Provides
    @SingleIn(MiniAppSessionScope::class)
    fun provideSession(
        component: FruitMergeComponent,
        interstitials: MiniAppInterstitialCapability,
    ): FruitMergeSession = FruitMergeSession(component, interstitials)
}
