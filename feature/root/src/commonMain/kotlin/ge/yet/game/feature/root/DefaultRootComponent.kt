package ge.yet.game.feature.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.replaceAll
import com.app.common.decompose.coroutineScope
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.lifecycle.doOnStart
import com.arkivanov.essenty.lifecycle.doOnStop
import dev.zacsweers.metro.Inject
import ge.yet.game.blockblast.component.game.GameComponent
import ge.yet.game.blockblast.component.result.BlockBlastResultSnapshot
import ge.yet.game.blockblast.component.result.GameResultComponent
import ge.yet.game.feature.home.HomeComponent
import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.domain.repository.AudioRepository
import ge.yet.game.domain.repository.SettingsRepository
import ge.yet.game.feature.settings.SettingsComponent
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
    private val settingsFactory: SettingsComponent.Factory,
    private val gameFactory: GameComponent.Factory,
    private val resultFactory: GameResultComponent.Factory,
    private val audio: AudioRepository,
    private val settingsRepository: SettingsRepository,
) : RootComponent, ComponentContext by componentContext {

    private val scope = coroutineScope()
    private val navigation = StackNavigation<Config>()
    private var lastGameInstanceId = 0L
    private val resultBackCallback = BackCallback(
        isEnabled = false,
        priority = BackCallback.PRIORITY_MAX,
        onBack = ::onBackClicked,
    )

    private val sheetNavigation = SlotNavigation<SheetConfig>()

    override val darkTheme: StateFlow<Boolean> = settingsRepository.darkTheme
    override val vibrationEnabled: StateFlow<Boolean> = settingsRepository.vibrationEnabled
    override val sfxEnabled: StateFlow<Boolean> = settingsRepository.sfxEnabled
    override val adsEnabled: StateFlow<Boolean> = settingsRepository.adsEnabled
    override fun onDismissSheet() {
        sheetNavigation.dismiss()
    }

    override val stack: Value<ChildStack<*, RootComponent.Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Home,
        handleBackButton = true,
        childFactory = ::createChild,
    )
    override val sheetSlot: Value<ChildSlot<*, RootComponent.SheetChild>> = childSlot(
        source = sheetNavigation,
        serializer = SheetConfig.serializer(),
        key = "RootSheet",
        handleBackButton = true,
        childFactory = ::createSheetChild,
    )

    init {
        backHandler.register(resultBackCallback)
        val stackSubscription = stack.subscribe {
            resultBackCallback.isEnabled = it.active.instance is RootComponent.Child.Result
        }
        lifecycle.doOnDestroy {
            stackSubscription.cancel()
            backHandler.unregister(resultBackCallback)
        }
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
        val result = stack.value.active.instance as? RootComponent.Child.Result
        if (result?.component?.onDismissReviewPrompt() == true) {
            return
        }
        if (result != null) {
            navigateHome()
        } else {
            navigation.pop()
        }
    }

    private fun createChild(
        config: Config,
        componentContext: ComponentContext,
    ): RootComponent.Child = when (config) {
        is Config.Home -> RootComponent.Child.Home(
            homeFactory.create(
                componentContext = componentContext,
                onContinueClicked = { navigation.bringToFront(newGameConfig(isNewGame = false)) },
                onNewGameClicked = { navigation.bringToFront(newGameConfig(isNewGame = true)) },
            )
        )

        is Config.Game -> {
            lastGameInstanceId = maxOf(lastGameInstanceId, config.instanceId)
            RootComponent.Child.Game(
                gameFactory.create(
                    componentContext = componentContext,
                    isNewGame = config.isNewGame,
                    restoredResultState = config.restoredResultState,
                    onExitClicked = { navigation.pop() },
                    onGameCompleted = { finalState, canContinue, shouldRequestReview ->
                        showResult(
                            gameInstanceId = config.instanceId,
                            finalState = finalState,
                            canContinue = canContinue,
                            shouldRequestReview = shouldRequestReview,
                        )
                    },
                    onSettingsClicked = { sheetNavigation.activate(SheetConfig.Settings) },
                    onReviveCompleted = { playableState ->
                        finishContinue(
                            gameInstanceId = config.instanceId,
                            playableState = playableState,
                        )
                    },
                    onReviveFailed = { failContinue(config.instanceId) },
                )
            )
        }

        is Config.Result -> RootComponent.Child.Result(
            resultFactory.create(
                componentContext = componentContext,
                snapshot = BlockBlastResultSnapshot.from(config.finalState),
                canContinue = config.canContinue,
                shouldRequestReview = config.shouldRequestReview,
                onContinueRequested = ::continueGame,
                onNewGameRequested = {
                    navigation.replaceAll(Config.Home, newGameConfig(isNewGame = true))
                },
                onHomeRequested = ::navigateHome,
            ),
        )
    }

    private fun createSheetChild(
        config: SheetConfig,
        componentContext: ComponentContext,
    ): RootComponent.SheetChild =
        when (config) {
            is SheetConfig.Settings -> RootComponent.SheetChild.Settings(
                component = settingsFactory.create(
                    componentContext = componentContext,
                    onBackClicked = ::onDismissSheet
                )
            )
        }


    private fun continueGame() {
        val game = stack.value.items
            .asReversed()
            .firstNotNullOfOrNull { it.instance as? RootComponent.Child.Game }
            ?.component

        if (game == null) {
            navigateHome()
            return
        }

        game.onReviveClicked()
    }

    private fun finishContinue(
        gameInstanceId: Long,
        playableState: GameState,
    ) {
        if (playableState.isGameOver) return

        navigation.navigate { configurations ->
            val activeResult = configurations.lastOrNull() as? Config.Result
            if (activeResult == null || activeResult.gameInstanceId != gameInstanceId) {
                configurations
            } else if (
                configurations.none {
                    it is Config.Game && it.instanceId == gameInstanceId
                }
            ) {
                configurations
            } else {
                configurations.dropLast(1).map { config ->
                    if (config is Config.Game && config.instanceId == gameInstanceId) {
                        config.copy(
                            isNewGame = false,
                            restoredResultState = null,
                        )
                    } else {
                        config
                    }
                }
            }
        }
    }

    private fun failContinue(gameInstanceId: Long) {
        val active = stack.value.active
        val config = active.configuration as? Config.Result
        if (config?.gameInstanceId != gameInstanceId) return
        (active.instance as? RootComponent.Child.Result)?.component?.onContinueFailed()
    }

    private fun navigateHome() {
        navigation.replaceAll(Config.Home)
    }

    private fun newGameConfig(isNewGame: Boolean): Config.Game =
        Config.Game(
            isNewGame = isNewGame,
            instanceId = ++lastGameInstanceId,
            restoredResultState = null,
        )

    private fun showResult(
        gameInstanceId: Long,
        finalState: GameState,
        canContinue: Boolean,
        shouldRequestReview: Boolean,
    ) {
        navigation.navigate { configurations ->
            if (configurations.lastOrNull() is Config.Result) {
                configurations
            } else {
                configurations.map { config ->
                    if (config is Config.Game && config.instanceId == gameInstanceId) {
                        config.copy(restoredResultState = finalState)
                    } else {
                        config
                    }
                } + Config.Result(
                    gameInstanceId = gameInstanceId,
                    finalState = finalState,
                    canContinue = canContinue,
                    shouldRequestReview = shouldRequestReview,
                )
            }
        }
    }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Home : Config

        @Serializable
        data class Game(
            val isNewGame: Boolean,
            val instanceId: Long,
            val restoredResultState: GameState?,
        ) : Config

        @Serializable
        data class Result(
            val gameInstanceId: Long,
            val finalState: GameState,
            val canContinue: Boolean,
            val shouldRequestReview: Boolean = false,
        ) : Config
    }

    @Serializable
    sealed interface SheetConfig {
        @Serializable
        data object Settings : SheetConfig
    }
}

@Inject
internal class DefaultRootComponentFactory(
    private val homeFactory: HomeComponent.Factory,
    private val settingsFactory: SettingsComponent.Factory,
    private val gameFactory: GameComponent.Factory,
    private val resultFactory: GameResultComponent.Factory,
    private val audio: AudioRepository,
    private val settingsRepository: SettingsRepository,
) : RootComponent.Factory {
    override fun create(componentContext: ComponentContext): RootComponent =
        DefaultRootComponent(
            componentContext = componentContext,
            homeFactory = homeFactory,
            settingsFactory = settingsFactory,
            gameFactory = gameFactory,
            resultFactory = resultFactory,
            audio = audio,
            settingsRepository = settingsRepository,
        )
}
