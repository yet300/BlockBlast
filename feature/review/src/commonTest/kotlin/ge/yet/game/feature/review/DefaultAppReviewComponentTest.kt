package ge.yet.game.feature.review

import com.app.common.AppDispatchers
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.domain.repository.ReviewCode
import ge.yet.game.domain.repository.SettingsRepository
import ge.yet.game.domain.repository.StoreReviewRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAppReviewComponentTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun creation_logs_shown_context() {
        val setup = build()

        assertEquals(listOf("review_prompt_shown"), setup.analytics.names)
        assertEquals(42L, setup.analytics.events.first().second?.get("score"))
        assertEquals("blockblast", setup.analytics.events.first().second?.get("source"))
    }

    @Test
    fun leave_feedback_requests_store_review_and_close_once() = runTest(dispatcher) {
        val setup = build()

        setup.component.onLeaveFeedbackClicked()
        setup.component.onLeaveFeedbackClicked()
        runCurrent()

        assertEquals(1, setup.storeReview.inAppRequests)
        assertEquals(1, setup.closeRequestCount)
        assertEquals(
            listOf("review_prompt_shown", "review_requested"),
            setup.analytics.names,
        )
    }

    @Test
    fun dont_show_again_suppresses_future_prompts_and_requests_close_once() = runTest(dispatcher) {
        val setup = build()

        setup.component.onDontShowAgainClicked()
        setup.component.onDontShowAgainClicked()
        runCurrent()

        assertEquals(2, setup.settings.reviewPromptCount.value)
        assertEquals(1, setup.closeRequestCount)
        assertEquals(
            listOf("review_prompt_shown", "review_prompt_suppressed"),
            setup.analytics.names,
        )
    }

    private fun build(): Setup {
        val settings = FakeSettings()
        val storeReview = RecordingStoreReview()
        val analytics = RecordingAnalytics()
        var closeRequestCount = 0
        val component = DefaultAppReviewComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            settings = settings,
            storeReview = storeReview,
            analytics = analytics,
            appScope = CoroutineScope(dispatcher),
            dispatchers = AppDispatchers(
                default = dispatcher,
                io = dispatcher,
                main = Dispatchers.Main,
                unconfined = dispatcher,
            ),
            analyticsParams = mapOf("source" to "blockblast", "score" to 42L),
            onCloseRequested = { closeRequestCount += 1 },
        )
        return Setup(
            component = component,
            settings = settings,
            storeReview = storeReview,
            analytics = analytics,
            closeRequestCountProvider = { closeRequestCount },
        )
    }

    private data class Setup(
        val component: DefaultAppReviewComponent,
        val settings: FakeSettings,
        val storeReview: RecordingStoreReview,
        val analytics: RecordingAnalytics,
        val closeRequestCountProvider: () -> Int,
    ) {
        val closeRequestCount: Int get() = closeRequestCountProvider()
    }

    private class FakeSettings : SettingsRepository {
        private val reviewCount = MutableStateFlow(0)
        override val musicEnabled = MutableStateFlow(true).asStateFlow()
        override val sfxEnabled = MutableStateFlow(true).asStateFlow()
        override val vibrationEnabled = MutableStateFlow(true).asStateFlow()
        override val darkTheme = MutableStateFlow(false).asStateFlow()
        override val adsEnabled = MutableStateFlow(true).asStateFlow()
        override val reviewPromptCount = reviewCount.asStateFlow()
        override suspend fun setMusicEnabled(enabled: Boolean) = Unit
        override suspend fun setSfxEnabled(enabled: Boolean) = Unit
        override suspend fun setVibrationEnabled(enabled: Boolean) = Unit
        override suspend fun setDarkTheme(enabled: Boolean) = Unit
        override suspend fun setAdsEnabled(enabled: Boolean) = Unit
        override suspend fun incrementReviewPromptCount() {
            reviewCount.value += 1
        }
        override suspend fun suppressReviewPrompts(max: Int) {
            reviewCount.value = maxOf(reviewCount.value, max)
        }
    }

    private class RecordingStoreReview : StoreReviewRepository {
        var inAppRequests: Int = 0
        override fun requestInAppReview(): Flow<ReviewCode> {
            inAppRequests += 1
            return flowOf(ReviewCode.NO_ERROR)
        }
        override fun requestInMarketReview(): Flow<ReviewCode> = flowOf(ReviewCode.NO_ERROR)
    }

    private class RecordingAnalytics : AnalyticRepository {
        val events = mutableListOf<Pair<String, Map<String, Any>?>>()
        val names: List<String> get() = events.map { it.first }
        override fun logEvent(eventName: String, params: Map<String, Any>?) {
            events += eventName to params
        }
        override fun deleteData() = Unit
    }
}
