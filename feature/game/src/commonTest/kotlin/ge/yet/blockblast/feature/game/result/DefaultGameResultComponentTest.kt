package ge.yet.blockblast.feature.game.result

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import ge.yet.blokblast.domain.model.Grid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        onContinueRequested: () -> Unit = {},
    ): Setup {
        val lifecycle = LifecycleRegistry()
        var continueCalls = 0
        var newGameCalls = 0
        var homeCalls = 0
        val component = DefaultGameResultComponent(
            componentContext = DefaultComponentContext(lifecycle),
            snapshot = snapshot,
            canContinue = canContinue,
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
        )
    }

    private data class Setup(
        val component: DefaultGameResultComponent,
        val lifecycle: LifecycleRegistry,
        val continueCallsProvider: () -> Int,
        val newGameCallsProvider: () -> Int,
        val homeCallsProvider: () -> Int,
    ) {
        val continueCalls: Int get() = continueCallsProvider()
        val newGameCalls: Int get() = newGameCallsProvider()
        val homeCalls: Int get() = homeCallsProvider()
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
