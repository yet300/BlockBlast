package ge.yet.blockblast.feature.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import dev.zacsweers.metro.Inject
import ge.yet.blockblast.feature.game.GameComponent
import ge.yet.blockblast.feature.home.HomeComponent
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Default implementation of [RootComponent].
 *
 * `internal` — consumers only see [RootComponent] through [RootComponent.Factory]
 * exposed by the feature DI graph.
 */
internal class DefaultRootComponent(
    componentContext: ComponentContext,
    private val homeFactory: HomeComponent.Factory,
    private val gameFactory: GameComponent.Factory,
    settingsRepository: SettingsRepository,
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    override val darkTheme: StateFlow<Boolean> = settingsRepository.darkTheme
    override val vibrationEnabled: StateFlow<Boolean> = settingsRepository.vibrationEnabled
    override val soundEnabled: StateFlow<Boolean> = settingsRepository.soundEnabled

    override val stack: Value<ChildStack<*, RootComponent.Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Home,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    override fun onBackClicked() {
        navigation.pop()
    }

    private fun createChild(
        config: Config,
        componentContext: ComponentContext,
    ): RootComponent.Child = when (config) {
        Config.Home -> RootComponent.Child.Home(
            homeFactory.create(
                componentContext = componentContext,
                onPlayClicked = { navigation.push(Config.Game) },
            )
        )

        Config.Game -> RootComponent.Child.Game(
            gameFactory.create(
                componentContext = componentContext,
                onExitClicked = { navigation.pop() },
            )
        )
    }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Home : Config

        @Serializable
        data object Game : Config
    }
}

/**
 * Metro-injectable factory wiring the Decompose [RootComponent] to the child
 * component factories. Exposed through [RootComponent.Factory] in the graph.
 */
@Inject
internal class DefaultRootComponentFactory(
    private val homeFactory: HomeComponent.Factory,
    private val gameFactory: GameComponent.Factory,
    private val settingsRepository: SettingsRepository,
) : RootComponent.Factory {
    override fun create(componentContext: ComponentContext): RootComponent =
        DefaultRootComponent(
            componentContext = componentContext,
            homeFactory = homeFactory,
            gameFactory = gameFactory,
            settingsRepository = settingsRepository,
        )
}
