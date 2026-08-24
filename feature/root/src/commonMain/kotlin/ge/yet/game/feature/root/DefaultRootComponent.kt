package ge.yet.game.feature.root

import com.app.common.decompose.coroutineScope
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.lifecycle.doOnStart
import com.arkivanov.essenty.lifecycle.doOnStop
import dev.zacsweers.metro.Inject
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.domain.repository.AudioRepository
import ge.yet.game.domain.repository.CrashlyticsRepository
import ge.yet.game.domain.repository.SettingsRepository
import ge.yet.game.feature.catalog.CatalogComponent
import ge.yet.game.feature.review.AppReviewComponent
import ge.yet.game.feature.review.policy.AppReviewPolicy
import ge.yet.game.feature.settings.SettingsComponent
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppDataResetter
import ge.yet.game.miniapp.api.MiniAppStorageProvider
import ge.yet.game.miniapp.audio.MiniAppAudioEngine
import ge.yet.game.miniapp.compose.MiniAppRegistry
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

internal class DefaultRootComponent(
    componentContext: ComponentContext,
    settingsRepository: SettingsRepository,
    reviewPolicy: AppReviewPolicy,
    miniAppRegistry: MiniAppRegistry,
    analytics: AnalyticRepository,
    crashlytics: CrashlyticsRepository,
    storageProvider: MiniAppStorageProvider,
    dataResetter: MiniAppDataResetter,
    miniAppAudioEngine: MiniAppAudioEngine,
    private val catalogFactory: CatalogComponent.Factory,
    private val settingsFactory: SettingsComponent.Factory,
    private val reviewFactory: AppReviewComponent.Factory,
    private val audio: AudioRepository,
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()
    private val sheetNavigation = SlotNavigation<SheetConfig>()

    override val sheetSlot: Value<ChildSlot<*, RootComponent.SheetChild>> = childSlot(
        source = sheetNavigation,
        serializer = SheetConfig.serializer(),
        key = "RootSheet",
        handleBackButton = true,
        childFactory = ::createSheetChild,
    )

    private val runtimeCoordinator = MiniAppRuntimeCoordinator(
        registry = miniAppRegistry,
        reviewPolicy = reviewPolicy,
        analytics = analytics,
        crashlytics = crashlytics,
        storageProvider = storageProvider,
        dataResetter = dataResetter,
        audioEngine = miniAppAudioEngine,
        initialForeground = lifecycle.state.isForeground,
        navigateToCatalog = { keepSheet ->
            navigation.replaceAll(Config.Catalog)
            if (!keepSheet && sheetSlot.value.child != null) sheetNavigation.dismiss()
        },
        showReview = { id, opportunity ->
            if (sheetSlot.value.child != null) {
                false
            } else {
                sheetNavigation.activate(
                    SheetConfig.AppReview(
                        miniAppId = id.value,
                        source = opportunity.triggerId,
                        score = opportunity.score,
                        bestScore = opportunity.bestScore,
                        revivesUsed = opportunity.revivesUsed,
                    ),
                )
                true
            }
        },
    ).also { coordinator ->
        coordinator.setObscured(sheetSlot.value.child != null)
    }

    override val darkTheme: StateFlow<Boolean> = settingsRepository.darkTheme
    override val adsEnabled: StateFlow<Boolean> = settingsRepository.adsEnabled

    override val stack: Value<ChildStack<*, RootComponent.Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Catalog,
        handleBackButton = false,
        childFactory = ::createChild,
    )

    private val backCallback = BackCallback(
        isEnabled = false,
        priority = BackCallback.PRIORITY_MAX,
        onBack = ::onBackClicked,
    )

    init {
        backHandler.register(backCallback)
        val stackSubscription = stack.subscribe { updateBackCallback() }
        val sheetSubscription = sheetSlot.subscribe { slot ->
            runtimeCoordinator.setObscured(slot.child != null)
            updateBackCallback()
        }
        lifecycle.doOnStart {
            runtimeCoordinator.setForeground(true)
            audio.onAppForeground()
        }
        lifecycle.doOnStop {
            runtimeCoordinator.setForeground(false)
            audio.onAppBackground()
        }
        lifecycle.doOnDestroy {
            stackSubscription.cancel()
            sheetSubscription.cancel()
            backHandler.unregister(backCallback)
        }
        updateBackCallback()
    }

    override fun onBackClicked() {
        when (val sheet = sheetSlot.value.child?.instance) {
            is RootComponent.SheetChild.Settings -> sheet.component.onBackClicked()
            is RootComponent.SheetChild.AppReview -> sheetNavigation.dismiss()
            null -> if (stack.value.active.configuration is Config.Running) {
                runtimeCoordinator.closeActiveSession()
            }
        }
    }

    override fun onSettingsClicked() {
        if (stack.value.active.configuration is Config.Running && sheetSlot.value.child == null) {
            sheetNavigation.activate(SheetConfig.Settings)
        }
    }

    override fun onDismissSheet() {
        sheetNavigation.dismiss()
    }

    private fun createChild(config: Config, componentContext: ComponentContext): RootComponent.Child =
        when (config) {
            Config.Catalog -> RootComponent.Child.Catalog(
                catalogFactory.create(componentContext, ::launchMiniApp),
            )
            is Config.Running -> createRunningChild(config, componentContext)
        }

    private fun launchMiniApp(id: MiniAppId) {
        runtimeCoordinator.launch(id) { key ->
            navigation.replaceAll(Config.Running(id, key))
        }
    }

    private fun createRunningChild(
        config: Config.Running,
        componentContext: ComponentContext,
    ): RootComponent.Child.RunningMiniApp {
        val scope = componentContext.coroutineScope()
        val state = runtimeCoordinator.createSession(
            id = config.id,
            key = config.key,
            componentContext = componentContext,
            scope = scope,
        )
        return RootComponent.Child.RunningMiniApp(config.id, state)
    }

    private fun createSheetChild(
        config: SheetConfig,
        componentContext: ComponentContext,
    ): RootComponent.SheetChild = when (config) {
        SheetConfig.Settings -> RootComponent.SheetChild.Settings(
            settingsFactory.create(
                componentContext = componentContext,
                clearGameData = runtimeCoordinator::clearMiniAppData,
                onBackClicked = ::onDismissSheet,
            ),
        )
        is SheetConfig.AppReview -> {
            val params = mutableMapOf<String, Any>(
                "mini_app_id" to config.miniAppId,
                "source" to config.source,
            )
            config.score?.let { params["score"] = it }
            config.bestScore?.let { params["best_score"] = it }
            config.revivesUsed?.let { params["revives_used"] = it }
            RootComponent.SheetChild.AppReview(
                reviewFactory.create(componentContext, params, ::onDismissSheet),
            )
        }
    }

    private fun updateBackCallback() {
        backCallback.isEnabled =
            sheetSlot.value.child != null || stack.value.active.configuration is Config.Running
    }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Catalog : Config

        @Serializable
        data class Running(val id: MiniAppId, val key: MiniAppSessionKey) : Config
    }

    @Serializable
    private sealed interface SheetConfig {
        @Serializable
        data object Settings : SheetConfig

        @Serializable
        data class AppReview(
            val miniAppId: String,
            val source: String,
            val score: Long?,
            val bestScore: Long?,
            val revivesUsed: Int?,
        ) : SheetConfig
    }

    private val Lifecycle.State.isForeground: Boolean
        get() = this == Lifecycle.State.STARTED || this == Lifecycle.State.RESUMED
}

@Inject
internal class DefaultRootComponentFactory(
    private val catalogFactory: CatalogComponent.Factory,
    private val settingsFactory: SettingsComponent.Factory,
    private val reviewFactory: AppReviewComponent.Factory,
    private val reviewPolicy: AppReviewPolicy,
    private val miniAppRegistry: MiniAppRegistry,
    private val audio: AudioRepository,
    private val settingsRepository: SettingsRepository,
    private val analytics: AnalyticRepository,
    private val crashlytics: CrashlyticsRepository,
    private val storageProvider: MiniAppStorageProvider,
    private val dataResetter: MiniAppDataResetter,
    private val miniAppAudioEngine: MiniAppAudioEngine,
) : RootComponent.Factory {
    override fun create(componentContext: ComponentContext): RootComponent = DefaultRootComponent(
        componentContext = componentContext,
        catalogFactory = catalogFactory,
        settingsFactory = settingsFactory,
        reviewFactory = reviewFactory,
        reviewPolicy = reviewPolicy,
        miniAppRegistry = miniAppRegistry,
        audio = audio,
        settingsRepository = settingsRepository,
        analytics = analytics,
        crashlytics = crashlytics,
        storageProvider = storageProvider,
        dataResetter = dataResetter,
        miniAppAudioEngine = miniAppAudioEngine,
    )
}
