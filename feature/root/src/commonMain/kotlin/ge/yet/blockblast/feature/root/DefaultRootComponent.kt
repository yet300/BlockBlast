package ge.yet.blockblast.feature.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.app.common.decompose.coroutineScope
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnStart
import com.arkivanov.essenty.lifecycle.doOnStop
import dev.zacsweers.metro.Inject
import ge.yet.blockblast.feature.game.GameComponent
import ge.yet.blockblast.feature.home.HomeComponent
import ge.yet.blokblast.domain.repository.AudioRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Default implementation of [RootComponent].
 *
 * Hooks into the process-level lifecycle so audio pauses when the app is
 * backgrounded (home button / app switcher / incoming call) and resumes
 * automatically when the user returns — on both Android and iOS, Decompose's
 * [ApplicationLifecycle] maps those OS events to `onStart`/`onStop`.
 */
internal class DefaultRootComponent(
    componentContext: ComponentContext,
    private val homeFactory: HomeComponent.Factory,
    private val gameFactory: GameComponent.Factory,
    private val audio: AudioRepository,
    private val settingsRepository: SettingsRepository,
) : RootComponent, ComponentContext by componentContext {

    private val scope = coroutineScope()
    private val navigation = StackNavigation<Config>()

    override val darkTheme: StateFlow<Boolean> = settingsRepository.darkTheme
    override val vibrationEnabled: StateFlow<Boolean> = settingsRepository.vibrationEnabled
    override val sfxEnabled: StateFlow<Boolean> = settingsRepository.sfxEnabled
    override val tutorialSeen: StateFlow<Boolean> = settingsRepository.tutorialSeen

    override fun onTutorialSeen() {
        scope.launch { settingsRepository.setTutorialSeen() }
    }

    override val stack: Value<ChildStack<*, RootComponent.Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Home,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    init {
        // App goes to background → pause audio immediately
        lifecycle.doOnStop {
            scope.launch { audio.onAppBackground() }
        }
        // App returns to foreground → resume if a game session was active
        lifecycle.doOnStart {
            scope.launch { audio.onAppForeground() }
        }
    }

    override fun onBackClicked() {
        navigation.pop()
    }

    private fun createChild(
        config: Config,
        componentContext: ComponentContext,
    ): RootComponent.Child = when (config) {
        is Config.Home -> RootComponent.Child.Home(
            homeFactory.create(
                componentContext = componentContext,
                onContinueClicked = { navigation.bringToFront(Config.Game(isNewGame = false)) },
                onNewGameClicked = { navigation.bringToFront(Config.Game(isNewGame = true)) },
            )
        )

        is Config.Game -> RootComponent.Child.Game(
            gameFactory.create(
                componentContext = componentContext,
                isNewGame = config.isNewGame,
                onExitClicked = { navigation.pop() },
            )
        )
    }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Home : Config

        @Serializable
        data class Game(val isNewGame: Boolean) : Config
    }
}

@Inject
internal class DefaultRootComponentFactory(
    private val homeFactory: HomeComponent.Factory,
    private val gameFactory: GameComponent.Factory,
    private val audio: AudioRepository,
    private val settingsRepository: SettingsRepository,
) : RootComponent.Factory {
    override fun create(componentContext: ComponentContext): RootComponent =
        DefaultRootComponent(
            componentContext = componentContext,
            homeFactory = homeFactory,
            gameFactory = gameFactory,
            audio = audio,
            settingsRepository = settingsRepository,
        )
}
