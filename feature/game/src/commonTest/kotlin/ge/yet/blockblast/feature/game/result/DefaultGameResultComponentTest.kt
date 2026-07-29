package ge.yet.blockblast.feature.game.result

import com.app.common.AppDispatchers
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.statekeeper.StateKeeper
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import ge.yet.blokblast.domain.model.Grid
import ge.yet.blokblast.domain.repository.AnalyticRepository
import ge.yet.blokblast.domain.repository.ReviewCode
import ge.yet.blokblast.domain.repository.SettingsRepository
import ge.yet.blokblast.domain.repository.StoreReviewRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultGameResultComponentTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun model_starts_in_continue_phase_when_continue_is_available() {
        val setup = build(canContinue = true)

        assertEquals(snapshot, setup.component.model.value.snapshot)
        assertEquals(5, setup.component.model.value.continueSecondsRemaining)
        assertTrue(setup.component.model.value.isContinuePhase)

        setup.lifecycle.destroy()
    }

    @Test
    fun countdown_ticks_until_primary_action_becomes_new_game() = runTest(testDispatcher) {
        val setup = build(canContinue = true)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(4, setup.component.model.value.continueSecondsRemaining)

        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(0, setup.component.model.value.continueSecondsRemaining)
        assertFalse(setup.component.model.value.isContinuePhase)

        setup.lifecycle.destroy()
    }

    @Test
    fun unavailable_continue_never_starts_a_countdown() = runTest(testDispatcher) {
        val setup = build(canContinue = false)

        assertEquals(0, setup.component.model.value.continueSecondsRemaining)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(0, setup.component.model.value.continueSecondsRemaining)
        assertFalse(setup.component.model.value.isContinuePhase)

        setup.lifecycle.destroy()
    }

    @Test
    fun review_prompt_is_visible_only_when_requested_and_dismiss_consumes_once() {
        val requested = build(canContinue = false, shouldRequestReview = true)
        val notRequested = build(canContinue = false, shouldRequestReview = false)

        assertNotNull(requested.component.reviewPrompt.value.component)
        assertNull(notRequested.component.reviewPrompt.value.component)
        assertTrue(requested.component.onDismissReviewPrompt())
        assertFalse(requested.component.onDismissReviewPrompt())
        assertNull(requested.component.reviewPrompt.value.component)

        requested.lifecycle.destroy()
        notRequested.lifecycle.destroy()
    }

    @Test
    fun review_leave_feedback_requests_store_review_and_consumes_prompt() = runTest(testDispatcher) {
        val setup = build(canContinue = false, shouldRequestReview = true)

        assertNotNull(setup.component.reviewPrompt.value.component).onLeaveFeedbackClicked()
        setup.lifecycle.destroy()
        runCurrent()

        assertEquals(1, setup.storeReview.inAppRequests)
        assertNull(setup.component.reviewPrompt.value.component)
    }

    @Test
    fun review_dont_show_again_suppresses_future_prompts_and_consumes() = runTest(testDispatcher) {
        val setup = build(canContinue = false, shouldRequestReview = true)

        assertNotNull(setup.component.reviewPrompt.value.component).onDontShowAgainClicked()
        runCurrent()

        assertEquals(2, setup.settings.reviewPromptCount.value)
        assertNull(setup.component.reviewPrompt.value.component)
        setup.lifecycle.destroy()
    }

    @Test
    fun dismissing_review_prompt_does_not_reset_continue_countdown() = runTest(testDispatcher) {
        val setup = build(canContinue = true, shouldRequestReview = true)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(3, setup.component.model.value.continueSecondsRemaining)

        setup.component.onDismissReviewPrompt()

        assertEquals(3, setup.component.model.value.continueSecondsRemaining)
        setup.lifecycle.destroy()
    }

    @Test
    fun consumed_review_prompt_stays_consumed_after_state_restore() {
        val stateKeeper = StateKeeperDispatcher()
        val first = build(
            canContinue = false,
            shouldRequestReview = true,
            stateKeeper = stateKeeper,
        )
        first.component.onDismissReviewPrompt()
        val saved = stateKeeper.save()
        first.lifecycle.destroy()

        val restored = build(
            canContinue = false,
            shouldRequestReview = true,
            stateKeeper = StateKeeperDispatcher(saved),
        )

        assertNull(restored.component.reviewPrompt.value.component)
        restored.lifecycle.destroy()
    }

    @Test
    fun open_review_prompt_is_restored_until_consumed() {
        val stateKeeper = StateKeeperDispatcher()
        val first = build(
            canContinue = false,
            shouldRequestReview = true,
            stateKeeper = stateKeeper,
        )
        val saved = stateKeeper.save()
        first.lifecycle.destroy()

        val restored = build(
            canContinue = false,
            shouldRequestReview = true,
            stateKeeper = StateKeeperDispatcher(saved),
        )

        assertNotNull(restored.component.reviewPrompt.value.component)
        restored.lifecycle.destroy()
    }

    @Test
    fun primary_action_continues_once_while_countdown_is_active() = runTest(testDispatcher) {
        val setup = build(canContinue = true)

        setup.component.onPrimaryClicked(approveImmediately)
        setup.component.onPrimaryClicked(approveImmediately)
        runCurrent()

        assertEquals(1, setup.continueCalls)
        assertEquals(0, setup.newGameCalls)
        setup.lifecycle.destroy()
    }

    @Test
    fun primary_action_starts_new_game_once_after_countdown_expires() = runTest(testDispatcher) {
        val setup = build(canContinue = true)
        advanceTimeBy(5_000)
        runCurrent()

        setup.component.onPrimaryClicked(failIfContinueGateRequested)
        setup.component.onPrimaryClicked(failIfContinueGateRequested)

        assertEquals(0, setup.continueCalls)
        assertEquals(1, setup.newGameCalls)
        setup.lifecycle.destroy()
    }

    @Test
    fun continue_click_keeps_continue_action_after_delayed_approval() = runTest(testDispatcher) {
        val setup = build(canContinue = true)
        var continueGateRequests = 0
        var approveContinue: (() -> Unit)? = null

        setup.component.onPrimaryClicked { onApproved ->
            continueGateRequests += 1
            approveContinue = onApproved
        }
        advanceTimeBy(6_000)
        runCurrent()
        approveContinue?.invoke()
        runCurrent()

        assertEquals(1, continueGateRequests)
        assertEquals(1, setup.continueCalls)
        assertEquals(0, setup.newGameCalls)
        setup.lifecycle.destroy()
    }

    @Test
    fun double_tap_requests_continue_gate_once_and_continues_once_after_approval() = runTest(testDispatcher) {
        val setup = build(canContinue = true)
        var continueGateRequests = 0
        var approveContinue: (() -> Unit)? = null
        val requestContinue: ((() -> Unit) -> Unit) = { onApproved ->
            continueGateRequests += 1
            approveContinue = onApproved
        }

        setup.component.onPrimaryClicked(requestContinue)
        setup.component.onPrimaryClicked(requestContinue)
        approveContinue?.invoke()
        approveContinue?.invoke()
        runCurrent()

        assertEquals(1, continueGateRequests)
        assertEquals(1, setup.continueCalls)
        assertEquals(0, setup.newGameCalls)
        setup.lifecycle.destroy()
    }

    @Test
    fun failed_continue_unlocks_retry_and_home_actions() = runTest(testDispatcher) {
        val setup = build(canContinue = true)

        setup.component.onPrimaryClicked(approveImmediately)
        runCurrent()
        assertEquals(1, setup.continueCalls)

        setup.component.onContinueFailed()
        setup.component.onPrimaryClicked(approveImmediately)
        runCurrent()
        assertEquals(2, setup.continueCalls)

        setup.component.onContinueFailed()
        setup.component.onHomeClicked()
        assertEquals(1, setup.homeCalls)
        setup.lifecycle.destroy()
    }

    @Test
    fun failed_continue_restarts_countdown_until_new_game_becomes_available() = runTest(testDispatcher) {
        val setup = build(canContinue = true)
        advanceTimeBy(4_000)
        runCurrent()
        setup.component.onPrimaryClicked(approveImmediately)
        runCurrent()

        setup.component.onContinueFailed()
        assertEquals(5, setup.component.model.value.continueSecondsRemaining)
        advanceTimeBy(5_000)
        runCurrent()
        setup.component.onPrimaryClicked(failIfContinueGateRequested)

        assertEquals(1, setup.continueCalls)
        assertEquals(1, setup.newGameCalls)
        setup.lifecycle.destroy()
    }

    @Test
    fun approval_after_destroy_does_not_continue() = runTest(testDispatcher) {
        val setup = build(canContinue = true)
        var approveContinue: (() -> Unit)? = null

        setup.component.onPrimaryClicked { onApproved ->
            approveContinue = onApproved
        }
        setup.lifecycle.destroy()
        approveContinue?.invoke()
        runCurrent()

        assertEquals(0, setup.continueCalls)
        assertEquals(0, setup.newGameCalls)
    }

    @Test
    fun approval_from_background_dispatcher_is_marshaled_to_main_once() = runTest(testDispatcher) {
        val mainConfinedValue = MutableValue(0)
        val setup = build(
            canContinue = true,
            onContinueRequested = {
                mainConfinedValue.value += 1
            },
        )
        var approveContinue: (() -> Unit)? = null

        setup.component.onPrimaryClicked { onApproved ->
            approveContinue = onApproved
        }
        withContext(Dispatchers.Default) {
            approveContinue?.invoke()
            approveContinue?.invoke()
        }

        runCurrent()
        assertEquals(1, setup.continueCalls)
        assertEquals(1, mainConfinedValue.value)
        assertEquals(0, setup.newGameCalls)
        setup.lifecycle.destroy()
    }

    @Test
    fun primary_action_starts_new_game_immediately_when_continue_is_unavailable() {
        val setup = build(canContinue = false)

        setup.component.onPrimaryClicked(failIfContinueGateRequested)

        assertEquals(0, setup.continueCalls)
        assertEquals(1, setup.newGameCalls)
        setup.lifecycle.destroy()
    }

    @Test
    fun home_is_a_single_terminal_action() {
        val setup = build(canContinue = true)

        setup.component.onHomeClicked()
        setup.component.onHomeClicked()
        setup.component.onPrimaryClicked(failIfContinueGateRequested)

        assertEquals(1, setup.homeCalls)
        assertEquals(0, setup.continueCalls)
        assertEquals(0, setup.newGameCalls)
        setup.lifecycle.destroy()
    }

    @Test
    fun destroy_cancels_the_countdown() = runTest(testDispatcher) {
        val setup = build(canContinue = true)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(4, setup.component.model.value.continueSecondsRemaining)

        setup.lifecycle.destroy()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(4, setup.component.model.value.continueSecondsRemaining)
    }

    private fun build(
        canContinue: Boolean,
        shouldRequestReview: Boolean = false,
        stateKeeper: StateKeeper? = null,
        onContinueRequested: () -> Unit = {},
    ): Setup {
        val lifecycle = LifecycleRegistry()
        val settings = FakeSettings()
        val storeReview = RecordingStoreReview()
        var continueCalls = 0
        var newGameCalls = 0
        var homeCalls = 0
        val component = DefaultGameResultComponent(
            componentContext = DefaultComponentContext(
                lifecycle = lifecycle,
                stateKeeper = stateKeeper,
            ),
            snapshot = snapshot,
            canContinue = canContinue,
            shouldRequestReview = shouldRequestReview,
            settings = settings,
            storeReview = storeReview,
            analytics = RecordingAnalytics(),
            appScope = CoroutineScope(testDispatcher),
            dispatchers = AppDispatchers(
                default = testDispatcher,
                io = testDispatcher,
                main = Dispatchers.Main,
                unconfined = testDispatcher,
            ),
            onContinueRequested = {
                continueCalls += 1
                onContinueRequested()
            },
            onNewGameRequested = { newGameCalls += 1 },
            onHomeRequested = { homeCalls += 1 },
        )
        lifecycle.resume()
        return Setup(
            component = component,
            lifecycle = lifecycle,
            continueCallsProvider = { continueCalls },
            newGameCallsProvider = { newGameCalls },
            homeCallsProvider = { homeCalls },
            settings = settings,
            storeReview = storeReview,
        )
    }

    private data class Setup(
        val component: DefaultGameResultComponent,
        val lifecycle: LifecycleRegistry,
        val continueCallsProvider: () -> Int,
        val newGameCallsProvider: () -> Int,
        val homeCallsProvider: () -> Int,
        val settings: FakeSettings,
        val storeReview: RecordingStoreReview,
    ) {
        val continueCalls: Int get() = continueCallsProvider()
        val newGameCalls: Int get() = newGameCallsProvider()
        val homeCalls: Int get() = homeCallsProvider()
    }

    private class FakeSettings : SettingsRepository {
        private val reviewFlow = MutableStateFlow(0)
        override val musicEnabled = MutableStateFlow(true).asStateFlow()
        override val sfxEnabled = MutableStateFlow(true).asStateFlow()
        override val vibrationEnabled = MutableStateFlow(true).asStateFlow()
        override val darkTheme = MutableStateFlow(false).asStateFlow()
        override val bestScore = MutableStateFlow(0L).asStateFlow()
        override val reviewPromptCount = reviewFlow.asStateFlow()
        override val tutorialSeen = MutableStateFlow(false).asStateFlow()
        override suspend fun setMusicEnabled(enabled: Boolean) = Unit
        override suspend fun setSfxEnabled(enabled: Boolean) = Unit
        override suspend fun setVibrationEnabled(enabled: Boolean) = Unit
        override suspend fun setDarkTheme(enabled: Boolean) = Unit
        override suspend fun setBestScore(score: Long) = Unit
        override suspend fun incrementReviewPromptCount() {
            reviewFlow.value += 1
        }
        override suspend fun suppressReviewPrompts(max: Int) {
            reviewFlow.value = maxOf(reviewFlow.value, max)
        }
        override suspend fun setTutorialSeen() = Unit
    }

    private class RecordingStoreReview : StoreReviewRepository {
        var inAppRequests = 0
        override fun requestInAppReview(): Flow<ReviewCode> {
            inAppRequests += 1
            return flowOf(ReviewCode.NO_ERROR)
        }
        override fun requestInMarketReview(): Flow<ReviewCode> = flowOf(ReviewCode.NO_ERROR)
    }

    private class RecordingAnalytics : AnalyticRepository {
        override fun logEvent(eventName: String, params: Map<String, Any>?) = Unit
        override fun deleteData() = Unit
    }

    private companion object {
        val approveImmediately: ((() -> Unit) -> Unit) = { onApproved -> onApproved() }
        val failIfContinueGateRequested: ((() -> Unit) -> Unit) = {
            error("Continue gate must not be requested")
        }
        val snapshot = BlockBlastResultSnapshot(
            score = 120L,
            bestScore = 200L,
            finalGrid = Grid(),
            isNewBest = false,
            revivesUsed = 0,
        )
    }
}
