package ge.yet.blockblast.feature.game

import com.app.common.config.AppConfig
import com.app.common.decompose.asValue
import com.app.common.decompose.coroutineScope
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import dev.zacsweers.metro.Inject
import ge.yet.blockblast.feature.game.integration.stateToModel
import ge.yet.blockblast.feature.game.reviewprompt.DefaultReviewPromptComponent
import ge.yet.blockblast.feature.game.store.GameAnalyticsLogger
import ge.yet.blockblast.feature.game.store.GameStore
import ge.yet.blockblast.feature.game.store.GameStoreFactory
import ge.yet.blockblast.feature.settings.SettingsComponent
import ge.yet.blokblast.domain.repository.AnalyticRepository
import ge.yet.blokblast.domain.repository.AudioRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import ge.yet.blokblast.domain.repository.StoreReviewRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

internal class DefaultGameComponent(
    componentContext: ComponentContext,
    analytics: AnalyticRepository,
    private val gameStoreFactory: GameStoreFactory,
    private val settingsComponent: SettingsComponent.Factory,
    private val audio: AudioRepository,
    private val settings: SettingsRepository,
    private val storeReview: StoreReviewRepository,
    private val isNewGame: Boolean,
    private val onExitClickedCb: () -> Unit,
) : ComponentContext by componentContext,
    GameComponent {
    private val store = instanceKeeper.getStore { gameStoreFactory.create(isNewGame = isNewGame) }
    private val sheetNavigation = SlotNavigation<SheetConfig>()
    private val lifecycleScope = coroutineScope()
    private val logger = GameAnalyticsLogger(analytics)

    override val model: Value<GameComponent.Model> = store.asValue().map(stateToModel)

    override val sheetSlot: Value<ChildSlot<*, GameComponent.SheetChild>> =
        childSlot(
            source = sheetNavigation,
            serializer = SheetConfig.serializer(),
            key = "GameSheet",
            handleBackButton = true,
            childFactory = ::createSheetChild,
        )

    init {
        // Stop music when the user navigates away (back button or exit)
        lifecycle.doOnDestroy { lifecycleScope.launch { audio.stopMusic() } }
        // One-shot effects from the store. Per the mvikotlin-code skill,
        // navigation/SDK calls live in the component, not the executor.
        lifecycleScope.launch {
            store.labels.collect { label ->
                when (label) {
                    GameStore.Label.RequestReview -> {
                        log("review_prompt_shown")
                        sheetNavigation.activate(SheetConfig.ReviewPrompt)
                    }
                }
            }
        }
    }


    override fun onCellClicked(pieceId: Long, x: Int, y: Int) {
        store.accept(GameStore.Intent.Place(pieceId, x, y))
    }

    override fun onReviveClicked() = store.accept(GameStore.Intent.Revive)
    override fun onRestartClicked() = store.accept(GameStore.Intent.Restart)
    override fun onSettingsClicked() {
        log("settings_opened")
        sheetNavigation.activate(SheetConfig.Settings)
    }

    override fun onExitClicked() {
        log("exit_clicked")
        onExitClickedCb()
    }

    override fun onDismissSheet() {
        when (sheetSlot.value.child?.instance) {
            is GameComponent.SheetChild.Settings -> log("settings_closed")
            is GameComponent.SheetChild.ReviewPrompt -> log("review_prompt_closed")
            null -> Unit
        }
        sheetNavigation.dismiss()
    }

    private fun onReviewPromptDontShowAgainClicked() {
        log("review_prompt_suppressed")
        lifecycleScope.launch {
            settings.suppressReviewPrompts(AppConfig.REVIEW_MAX_PROMPTS)
        }
    }

    private fun onReviewPromptLeaveFeedbackClicked() {
        log("review_requested")
        lifecycleScope.launch {
            storeReview.requestInAppReview().collect {}
        }
    }

    private fun log(eventName: String) = logger.log(eventName, store.state.game)

    private fun createSheetChild(
        config: SheetConfig,
        componentContext: ComponentContext,
    ): GameComponent.SheetChild =
        when (config) {
            is SheetConfig.Settings ->
                GameComponent.SheetChild.Settings(
                    settingsComponent.create(
                        componentContext = componentContext,
                        onBackClicked = ::onDismissSheet
                    ),
                )
            is SheetConfig.ReviewPrompt ->
                GameComponent.SheetChild.ReviewPrompt(
                    DefaultReviewPromptComponent(
                        componentContext = componentContext,
                        onDontShowAgainRequested = ::onReviewPromptDontShowAgainClicked,
                        onDismissed = { sheetNavigation.dismiss() },
                        onReviewRequested = ::onReviewPromptLeaveFeedbackClicked,
                    ),
                )
        }

    @Serializable
    sealed interface SheetConfig {
        @Serializable
        data object Settings : SheetConfig

        @Serializable
        data object ReviewPrompt : SheetConfig
    }
}


@Inject
internal class DefaultGameComponentFactory(
    private val gameStoreFactory: GameStoreFactory,
    private val settingsComponent: SettingsComponent.Factory,
    private val audio: AudioRepository,
    private val settings: SettingsRepository,
    private val storeReview: StoreReviewRepository,
    private val analytics: AnalyticRepository,
) : GameComponent.Factory {
    override fun create(
        componentContext: ComponentContext,
        isNewGame: Boolean,
        onExitClicked: () -> Unit,
    ): GameComponent = DefaultGameComponent(
        componentContext = componentContext,
        gameStoreFactory = gameStoreFactory,
        settingsComponent = settingsComponent,
        audio = audio,
        settings = settings,
        storeReview = storeReview,
        analytics = analytics,
        isNewGame = isNewGame,
        onExitClickedCb = onExitClicked,
    )
}
