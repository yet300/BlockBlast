package ge.yet.game.feature.review

import com.app.common.AppDispatchers
import com.app.common.config.AppConfig
import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.Inject
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.domain.repository.StoreReviewRepository
import ge.yet.game.feature.review.domain.repository.ReviewPromptRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class DefaultAppReviewComponent(
    componentContext: ComponentContext,
    private val reviewPromptRepository: ReviewPromptRepository,
    private val storeReview: StoreReviewRepository,
    private val analytics: AnalyticRepository,
    private val appScope: CoroutineScope,
    private val dispatchers: AppDispatchers,
    private val analyticsParams: Map<String, Any>,
    private val onCloseRequested: () -> Unit,
) : AppReviewComponent,
    ComponentContext by componentContext {

    private var handled = false

    init {
        logReview("review_prompt_shown")
    }

    override fun onDontShowAgainClicked() {
        if (!claimAction()) return
        logReview("review_prompt_suppressed")
        launchReviewSideEffect("review_prompt_suppress_failed") {
            reviewPromptRepository.suppressPrompts(AppConfig.REVIEW_MAX_PROMPTS)
        }
        onCloseRequested()
    }

    override fun onLeaveFeedbackClicked() {
        if (!claimAction()) return
        logReview("review_requested")
        launchReviewSideEffect("review_request_failed") {
            storeReview.requestInAppReview().collect {}
        }
        onCloseRequested()
    }

    private fun claimAction(): Boolean {
        if (handled) return false
        handled = true
        return true
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
        analytics.logEvent(eventName = eventName, params = analyticsParams)
    }
}

@Inject
internal class DefaultAppReviewComponentFactory(
    private val reviewPromptRepository: ReviewPromptRepository,
    private val storeReview: StoreReviewRepository,
    private val analytics: AnalyticRepository,
    private val appScope: CoroutineScope,
    private val dispatchers: AppDispatchers,
) : AppReviewComponent.Factory {
    override fun create(
        componentContext: ComponentContext,
        analyticsParams: Map<String, Any>,
        onCloseRequested: () -> Unit,
    ): AppReviewComponent =
        DefaultAppReviewComponent(
            componentContext = componentContext,
            reviewPromptRepository = reviewPromptRepository,
            storeReview = storeReview,
            analytics = analytics,
            appScope = appScope,
            dispatchers = dispatchers,
            analyticsParams = analyticsParams,
            onCloseRequested = onCloseRequested,
        )
}
