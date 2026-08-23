package ge.yet.game.feature.settings.reset

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import ge.yet.game.miniapp.api.MiniAppDataResetResult
import ge.yet.game.miniapp.api.MiniAppId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultResetGameDataComponentTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun confirm_runs_once_while_clearing_and_maps_success() = runTest {
        val result = CompletableDeferred<MiniAppDataResetResult>()
        var calls = 0
        val component = component(clearGameData = {
            calls += 1
            result.await()
        })

        component.onConfirmClicked()
        component.onConfirmClicked()
        runCurrent()

        assertEquals(1, calls)
        assertIs<ResetGameDataComponent.Status.Clearing>(component.model.value.status)

        result.complete(MiniAppDataResetResult.Success)
        runCurrent()

        assertIs<ResetGameDataComponent.Status.Success>(component.model.value.status)
    }

    @Test
    fun partial_failure_is_explicit_and_retry_runs_again() = runTest {
        val failedId = MiniAppId("game.blocks")
        var calls = 0
        val component = component(clearGameData = {
            calls += 1
            if (calls == 1) {
                MiniAppDataResetResult.PartialFailure(setOf(failedId))
            } else {
                MiniAppDataResetResult.Success
            }
        })

        component.onConfirmClicked()
        runCurrent()
        assertEquals(
            setOf(failedId),
            assertIs<ResetGameDataComponent.Status.PartialFailure>(
                component.model.value.status,
            ).failedMiniAppIds,
        )

        component.onRetryClicked()
        runCurrent()

        assertEquals(2, calls)
        assertIs<ResetGameDataComponent.Status.Success>(component.model.value.status)
    }

    @Test
    fun back_before_confirmation_does_not_clear_data() = runTest {
        var calls = 0
        var backCalls = 0
        val component = component(
            clearGameData = {
                calls += 1
                MiniAppDataResetResult.Success
            },
            onBack = { backCalls += 1 },
        )

        component.onBackClicked()
        runCurrent()

        assertEquals(0, calls)
        assertEquals(1, backCalls)
    }

    @Test
    fun destroying_child_cancels_the_active_clear() = runTest {
        val lifecycle = LifecycleRegistry().also(LifecycleRegistry::resume)
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val component = DefaultResetGameDataComponent(
            componentContext = DefaultComponentContext(lifecycle),
            clearGameData = {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            },
            onBackClickedCb = {},
        )

        component.onConfirmClicked()
        withTimeout(1_000) { started.await() }
        lifecycle.destroy()
        withTimeout(1_000) {
            while (!cancelled) yield()
        }

        assertTrue(cancelled)
    }

    private fun kotlinx.coroutines.test.TestScope.component(
        clearGameData: suspend () -> MiniAppDataResetResult,
        onBack: () -> Unit = {},
    ) = DefaultResetGameDataComponent(
        componentContext = DefaultComponentContext(LifecycleRegistry()),
        clearGameData = clearGameData,
        onBackClickedCb = onBack,
        coroutineScope = backgroundScope,
    )
}
