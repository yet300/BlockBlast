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
import ge.yet.game.domain.repository.SettingsRepository
import ge.yet.game.feature.catalog.CatalogComponent
import ge.yet.game.feature.review.AppReviewComponent
import ge.yet.game.feature.review.policy.AppReviewPolicy
import ge.yet.game.feature.settings.SettingsComponent
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppReviewOpportunity
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.compose.MiniAppPlugin
import ge.yet.game.miniapp.compose.MiniAppRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

internal class DefaultRootComponent(
    componentContext: ComponentContext,
    settingsRepository: SettingsRepository,
    private val catalogFactory: CatalogComponent.Factory,
    private val settingsFactory: SettingsComponent.Factory,
    private val reviewFactory: AppReviewComponent.Factory,
    private val reviewPolicy: AppReviewPolicy,
    private val miniAppRegistry: MiniAppRegistry,
    private val audio: AudioRepository,
    private val analytics: AnalyticRepository,
) : RootComponent, ComponentContext by componentContext {

    private val rootScope = coroutineScope()
    private val navigation = StackNavigation<Config>()
    private val sheetNavigation = SlotNavigation<SheetConfig>()
    private var lastSessionKey = 0L
    private var playInProgress = false
    private var pendingPlugin: Pair<SessionKey, MiniAppPlugin>? = null
    private var isForeground = lifecycle.state.isForeground
    private var isObscured = false
    private var activeSessionKey: SessionKey? = null
    private var activeVisibilitySource: DefaultMiniAppVisibilitySource? = null

    override val darkTheme: StateFlow<Boolean> = settingsRepository.darkTheme
    override val adsEnabled: StateFlow<Boolean> = settingsRepository.adsEnabled

    override val stack: Value<ChildStack<*, RootComponent.Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Catalog,
        handleBackButton = false,
        childFactory = ::createChild,
    )

    override val sheetSlot: Value<ChildSlot<*, RootComponent.SheetChild>> = childSlot(
        source = sheetNavigation,
        serializer = SheetConfig.serializer(),
        key = "RootSheet",
        handleBackButton = true,
        childFactory = ::createSheetChild,
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
            isObscured = slot.child != null
            updateActiveVisibility()
            updateBackCallback()
        }
        lifecycle.doOnStart {
            isForeground = true
            updateActiveVisibility()
            rootScope.launch { audio.onAppForeground() }
        }
        lifecycle.doOnStop {
            isForeground = false
            updateActiveVisibility()
            rootScope.launch { audio.onAppBackground() }
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
                navigation.replaceAll(Config.Catalog)
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
        if (playInProgress || stack.value.active.configuration is Config.Running) return
        val plugin = miniAppRegistry[id]
        if (plugin == null) {
            analytics.logEvent("miniapp_launch_missing", mapOf("miniapp_id" to id.value))
            return
        }
        playInProgress = true
        val key = SessionKey(++lastSessionKey)
        pendingPlugin = key to plugin
        try {
            navigation.replaceAll(Config.Running(id, key))
        } finally {
            pendingPlugin = null
            playInProgress = false
        }
    }

    private fun createRunningChild(
        config: Config.Running,
        componentContext: ComponentContext,
    ): RootComponent.Child.RunningMiniApp {
        lastSessionKey = maxOf(lastSessionKey, config.key.value)
        val scope = componentContext.coroutineScope()
        val visibility = DefaultMiniAppVisibilitySource()
        val host = BoundMiniAppSessionHost(config.key, config.id, scope)
        activeSessionKey = config.key
        activeVisibilitySource = visibility
        visibility.set(currentVisibility())
        componentContext.lifecycle.doOnDestroy {
            if (activeSessionKey == config.key) {
                activeSessionKey = null
                activeVisibilitySource = null
            }
        }

        val plugin = pendingPlugin?.takeIf { it.first == config.key }?.second ?: miniAppRegistry[config.id]
        val state = if (plugin == null) {
            analytics.logEvent("miniapp_launch_missing", mapOf("miniapp_id" to config.id.value))
            RootComponent.MiniAppState.Unavailable(config.id)
        } else {
            createSessionState(plugin, config, componentContext, visibility, host)
        }
        return RootComponent.Child.RunningMiniApp(config.id, state)
    }

    private fun createSessionState(
        plugin: MiniAppPlugin,
        config: Config.Running,
        componentContext: ComponentContext,
        visibility: MiniAppVisibilitySource,
        host: BoundMiniAppSessionHost,
    ): RootComponent.MiniAppState = try {
        val session = plugin.createSession(componentContext, visibility, host)
        host.arm()
        RootComponent.MiniAppState.Content(session)
    } catch (error: Throwable) {
        analytics.logEvent(
            "miniapp_launch_failed",
            mapOf(
                "miniapp_id" to config.id.value,
                "error" to (error::class.simpleName ?: "Unknown"),
            ),
        )
        RootComponent.MiniAppState.Unavailable(config.id)
    }

    private fun createSheetChild(
        config: SheetConfig,
        componentContext: ComponentContext,
    ): RootComponent.SheetChild = when (config) {
        SheetConfig.Settings -> RootComponent.SheetChild.Settings(
            settingsFactory.create(componentContext, ::onDismissSheet),
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

    private fun updateActiveVisibility() {
        val active = stack.value.active.configuration as? Config.Running ?: return
        if (activeSessionKey == active.key) {
            activeVisibilitySource?.set(currentVisibility())
        }
    }

    private fun currentVisibility(): MiniAppVisibility = when {
        !isForeground -> MiniAppVisibility.BACKGROUND
        isObscured -> MiniAppVisibility.OBSCURED
        else -> MiniAppVisibility.ACTIVE
    }

    private fun isActive(key: SessionKey): Boolean =
        (stack.value.active.configuration as? Config.Running)?.key == key

    private inner class BoundMiniAppSessionHost(
        private val key: SessionKey,
        private val miniAppId: MiniAppId,
        private val scope: CoroutineScope,
    ) : MiniAppSessionHost {
        private var armed = false

        fun arm() {
            armed = true
        }

        override fun close() {
            if (!armed) return
            scope.launch {
                if (!isActive(key)) return@launch
                if (sheetSlot.value.child != null) sheetNavigation.dismiss()
                if (isActive(key)) navigation.replaceAll(Config.Catalog)
            }
        }

        override fun requestReview(opportunity: MiniAppReviewOpportunity) {
            if (!armed) return
            scope.launch {
                if (!isActive(key) || sheetSlot.value.child != null) return@launch
                var acquired = false
                var committed = false
                try {
                    acquired = reviewPolicy.tryAcquirePrompt()
                    if (!acquired || !isActive(key) || sheetSlot.value.child != null) return@launch
                    sheetNavigation.activate(
                        SheetConfig.AppReview(
                            miniAppId = miniAppId.value,
                            source = opportunity.triggerId,
                            score = opportunity.score,
                            bestScore = opportunity.bestScore,
                            revivesUsed = opportunity.revivesUsed,
                        ),
                    )
                    committed = true
                } finally {
                    if (acquired && !committed) {
                        withContext(NonCancellable) { reviewPolicy.releasePrompt() }
                    }
                }
            }
        }
    }

    private class DefaultMiniAppVisibilitySource : MiniAppVisibilitySource {
        private val mutableVisibility = MutableStateFlow(MiniAppVisibility.BACKGROUND)
        override val visibility: StateFlow<MiniAppVisibility> = mutableVisibility.asStateFlow()
        fun set(value: MiniAppVisibility) {
            mutableVisibility.value = value
        }
    }

    @Serializable
    @JvmInline
    private value class SessionKey(val value: Long)

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Catalog : Config

        @Serializable
        data class Running(val id: MiniAppId, val key: SessionKey) : Config
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
    )
}
