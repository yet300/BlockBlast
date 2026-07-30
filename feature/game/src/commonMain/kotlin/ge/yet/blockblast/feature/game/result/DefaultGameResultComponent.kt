package ge.yet.blockblast.feature.game.result

import com.app.common.AppDispatchers
import com.app.common.config.AppConfig
import com.app.common.decompose.coroutineScope
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import dev.zacsweers.metro.Inject
import ge.yet.blockblast.feature.game.reviewprompt.DefaultReviewPromptComponent
import ge.yet.blokblast.domain.repository.AnalyticRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import ge.yet.blokblast.domain.repository.StoreReviewRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

internal class DefaultGameResultComponent(
    componentContext: ComponentContext,
    snapshot: BlockBlastResultSnapshot,
    canContinue: Boolean,
    shouldRequestReview: Boolean,
    private val settings: SettingsRepository,
    private val storeReview: StoreReviewRepository,
    private val analytics: AnalyticRepository,
    private val appScope: CoroutineScope,
    private val dispatchers: AppDispatchers,
    private val onContinueRequested: () -> Unit,
    private val onNewGameRequested: () -> Unit,
    private val onHomeRequested: () -> Unit,
) : GameResultComponent,
    ComponentContext by componentContext {

    private val componentScope = coroutineScope()
    private var reviewPromptConsumed =
        stateKeeper.consume(REVIEW_PROMPT_STATE_KEY, ReviewPromptState.serializer())
            ?.consumed == true
    private val modelState = MutableValue(
        GameResultComponent.Model(
            snapshot = snapshot,
            canContinue = canContinue,
            continueSecondsRemaining = if (canContinue) CONTINUE_COUNTDOWN_SECONDS else 0,
        ),
    )
    override val model: Value<GameResultComponent.Model> = modelState
    private val reviewPromptState = MutableValue(
        GameResultComponent.ReviewPromptSlot(
            component = if (shouldRequestReview && !reviewPromptConsumed) {
                DefaultReviewPromptComponent(
                    componentContext = childContext(key = "ReviewPrompt"),
                    onDontShowAgainRequested = ::onReviewPromptDontShowAgainClicked,
                    onDismissed = { onDismissReviewPrompt() },
                    onReviewRequested = ::onReviewPromptLeaveFeedbackClicked,
                )
            } else {
                null
            },
        ),
    )
    override val reviewPrompt: Value<GameResultComponent.ReviewPromptSlot> = reviewPromptState

    private var countdownJob: Job? = null
    private var terminalActionHandled = false

    init {
        stateKeeper.register(
            REVIEW_PROMPT_STATE_KEY,
            ReviewPromptState.serializer(),
        ) {
            ReviewPromptState(consumed = reviewPromptConsumed)
        }
        if (reviewPromptState.value.component != null) logReview("review_prompt_shown")
        startCountdown()
    }

    override fun onPrimaryClicked(requestContinue: (onApproved: () -> Unit) -> Unit) {
        if (!claimTerminalAction()) return
        val continueSelected = modelState.value.isContinuePhase
        countdownJob?.cancel()

        if (continueSelected) {
            if (!settings.adsEnabled.value) {
                componentScope.launch {
                    onContinueRequested()
                }
                return
            }
            var approvalHandled = false
            requestContinue {
                componentScope.launch {
                    if (approvalHandled) return@launch
                    approvalHandled = true
                    onContinueRequested()
                }
            }
        } else {
            onNewGameRequested()
        }
    }

    override fun onHomeClicked() {
        if (!claimTerminalAction()) return
        countdownJob?.cancel()
        onHomeRequested()
    }

    override fun onContinueFailed() {
        terminalActionHandled = false
        modelState.value = modelState.value.copy(
            continueSecondsRemaining = CONTINUE_COUNTDOWN_SECONDS,
        )
        startCountdown()
    }

    override fun onDismissReviewPrompt(): Boolean {
        if (reviewPromptState.value.component == null) return false
        reviewPromptConsumed = true
        reviewPromptState.value = GameResultComponent.ReviewPromptSlot(component = null)
        logReview("review_prompt_closed")
        return true
    }

    private fun onReviewPromptDontShowAgainClicked() {
        logReview("review_prompt_suppressed")
        launchReviewSideEffect("review_prompt_suppress_failed") {
            settings.suppressReviewPrompts(AppConfig.REVIEW_MAX_PROMPTS)
        }
    }

    private fun onReviewPromptLeaveFeedbackClicked() {
        logReview("review_requested")
        launchReviewSideEffect("review_request_failed") {
            storeReview.requestInAppReview().collect {}
        }
    }

    private fun launchReviewSideEffect(
        failureEvent: String,
        block: suspend () -> Unit,
    ) {
        appScope.launch(dispatchers.main.immediate) {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logReview(failureEvent)
            }
        }
    }

    private fun logReview(eventName: String) {
        val snapshot = modelState.value.snapshot
        analytics.logEvent(
            eventName = eventName,
            params = mapOf(
                "score" to snapshot.score,
                "best_score" to snapshot.bestScore,
                "revives_used" to snapshot.revivesUsed,
            ),
        )
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        if (!modelState.value.isContinuePhase) return
        countdownJob = componentScope.launch {
            while (modelState.value.continueSecondsRemaining > 0) {
                delay(COUNTDOWN_TICK_MILLIS)
                modelState.value = modelState.value.copy(
                    continueSecondsRemaining =
                        (modelState.value.continueSecondsRemaining - 1).coerceAtLeast(0),
                )
            }
        }
    }

    private fun claimTerminalAction(): Boolean {
        if (terminalActionHandled) return false
        terminalActionHandled = true
        return true
    }

    private companion object {
        const val CONTINUE_COUNTDOWN_SECONDS = 5
        const val COUNTDOWN_TICK_MILLIS = 1_000L
        const val REVIEW_PROMPT_STATE_KEY = "ReviewPromptState"
    }

    @Serializable
    private data class ReviewPromptState(
        val consumed: Boolean = false,
    )
}

@Inject
internal class DefaultGameResultComponentFactory(
    private val settings: SettingsRepository,
    private val storeReview: StoreReviewRepository,
    private val analytics: AnalyticRepository,
    private val appScope: CoroutineScope,
    private val dispatchers: AppDispatchers,
) : GameResultComponent.Factory {
    override fun create(
        componentContext: ComponentContext,
        snapshot: BlockBlastResultSnapshot,
        canContinue: Boolean,
        shouldRequestReview: Boolean,
        onContinueRequested: () -> Unit,
        onNewGameRequested: () -> Unit,
        onHomeRequested: () -> Unit,
    ): GameResultComponent =
        DefaultGameResultComponent(
            componentContext = componentContext,
            snapshot = snapshot,
            canContinue = canContinue,
            shouldRequestReview = shouldRequestReview,
            settings = settings,
            storeReview = storeReview,
            analytics = analytics,
            appScope = appScope,
            dispatchers = dispatchers,
            onContinueRequested = onContinueRequested,
            onNewGameRequested = onNewGameRequested,
            onHomeRequested = onHomeRequested,
        )
}
