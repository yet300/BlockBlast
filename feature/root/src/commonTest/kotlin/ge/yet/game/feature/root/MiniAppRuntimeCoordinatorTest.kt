package ge.yet.game.feature.root

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.app.common.decompose.coroutineScope
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.domain.repository.CrashlyticsRepository
import ge.yet.game.feature.review.policy.AppReviewPolicy
import ge.yet.game.miniapp.api.MiniAppCategoryId
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppReviewOpportunity
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.compose.MiniAppManifest
import ge.yet.game.miniapp.compose.MiniAppPlugin
import ge.yet.game.miniapp.compose.MiniAppRegistry
import ge.yet.game.miniapp.compose.MiniAppSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.StringResource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class, InternalResourceApi::class)
class MiniAppRuntimeCoordinatorTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val lifecycles = mutableListOf<LifecycleRegistry>()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() {
        try {
            lifecycles.forEach(LifecycleRegistry::destroy)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun launch_and_session_creation_publish_context_and_breadcrumbs() {
        val setup = build()
        var state: RootComponent.MiniAppState? = null

        setup.coordinator.launch(FIRST_ID) { key ->
            assertEquals(MiniAppSessionKey(1), key)
            state = setup.coordinator.createSessionWithChildScope(FIRST_ID, key, componentContext())
        }

        assertIs<RootComponent.MiniAppState.Content>(state)
        assertEquals(listOf(FIRST_ID), setup.registry.lookups)
        assertEquals(1, setup.firstPlugin.createCount)
        assertEquals(MiniAppVisibility.ACTIVE, setup.firstPlugin.visibility.single().visibility.value)
        assertEquals(
            listOf(
                "miniapp_launch_requested id=game.alpha key=1",
                "miniapp_session_created id=game.alpha key=1 visibility=ACTIVE",
            ),
            setup.crashlytics.messages,
        )
        assertEquals(FIRST_ID.value, setup.crashlytics.values["mini_app_id"])
        assertEquals(1L, setup.crashlytics.values["mini_app_session_key"])
        assertEquals("ACTIVE", setup.crashlytics.values["mini_app_visibility"])
        assertEquals("active", setup.crashlytics.values["mini_app_state"])
    }

    @Test
    fun missing_plugin_logs_analytics_and_crash_breadcrumb_without_navigating() {
        val setup = build()
        val missing = MiniAppId("game.missing")
        var navigations = 0

        setup.coordinator.launch(missing) { navigations += 1 }

        assertEquals(0, navigations)
        assertEquals(listOf(missing), setup.registry.lookups)
        assertEquals(
            listOf<Pair<String, Map<String, Any>?>>(
                "miniapp_launch_missing" to mapOf("miniapp_id" to missing.value),
            ),
            setup.analytics.events,
        )
        assertEquals(
            listOf(
                "miniapp_launch_requested id=game.missing",
                "miniapp_launch_missing id=game.missing",
            ),
            setup.crashlytics.messages,
        )
        assertEquals(missing.value, setup.crashlytics.values["mini_app_id"])
        assertEquals("", setup.crashlytics.values["mini_app_session_key"])
        assertEquals("", setup.crashlytics.values["mini_app_visibility"])
        assertEquals("unavailable", setup.crashlytics.values["mini_app_state"])
    }

    @Test
    fun factory_failure_returns_unavailable_and_records_original_exception() = runTest {
        val failure = IllegalStateException("broken")
        val plugin = RecordingPlugin(FIRST_ID, failure)
        val setup = build(firstPlugin = plugin)
        lateinit var state: RootComponent.MiniAppState

        setup.coordinator.launch(FIRST_ID) { key ->
            state = setup.coordinator.createSessionWithChildScope(FIRST_ID, key, componentContext())
        }

        assertEquals(RootComponent.MiniAppState.Unavailable(FIRST_ID), state)
        assertSame(failure, setup.crashlytics.exceptions.single())
        assertEquals("miniapp_launch_failed", setup.analytics.events.single().first)
        assertEquals("IllegalStateException", setup.analytics.events.single().second?.get("error"))
        assertEquals("unavailable", setup.crashlytics.values["mini_app_state"])
        plugin.hosts.single().close()
        plugin.hosts.single().requestReview(MiniAppReviewOpportunity("ignored"))
        runCurrent()
        assertEquals(0, setup.closeCalls)
        assertEquals(0, setup.reviewPolicy.acquireCalls)
    }

    @Test
    fun visibility_changes_update_active_source_and_crash_context_without_recreation() {
        val setup = build()
        setup.launchAndCreate(FIRST_ID, componentContext())
        val source = setup.firstPlugin.visibility.single()

        setup.coordinator.setObscured(true)
        assertEquals(MiniAppVisibility.OBSCURED, source.visibility.value)
        setup.coordinator.setObscured(true)
        setup.coordinator.setForeground(false)
        assertEquals(MiniAppVisibility.BACKGROUND, source.visibility.value)
        setup.coordinator.setObscured(false)
        setup.coordinator.setForeground(true)
        assertEquals(MiniAppVisibility.ACTIVE, source.visibility.value)

        assertEquals(1, setup.firstPlugin.createCount)
        assertEquals(
            listOf("OBSCURED", "BACKGROUND", "ACTIVE"),
            setup.crashlytics.messages
                .filter { it.startsWith("miniapp_visibility_changed") }
                .map { it.substringAfter("visibility=") },
        )
        assertEquals("ACTIVE", setup.crashlytics.values["mini_app_visibility"])
    }

    @Test
    fun visibility_collector_close_and_destroy_suppresses_post_close_diagnostics() = runTest {
        lateinit var childLifecycle: LifecycleRegistry
        val plugin = RecordingPlugin(FIRST_ID, closeOnVisibility = MiniAppVisibility.OBSCURED)
        val setup = build(
            firstPlugin = plugin,
            afterClose = { childLifecycle.destroy() },
        )
        childLifecycle = lifecycle().also(LifecycleRegistry::resume)
        val componentContext = DefaultComponentContext(childLifecycle)
        setup.launchAndCreate(FIRST_ID, componentContext)

        setup.coordinator.setObscured(true)
        runCurrent()

        assertEquals(1, setup.closeCalls)
        assertEquals("", setup.crashlytics.values["mini_app_visibility"])
        val closeIndex = setup.crashlytics.operations.indexOfFirst {
            it is CrashOperation.Message && it.value.startsWith("miniapp_session_closed")
        }
        assertFalse(closeIndex < 0)
        assertFalse(
            setup.crashlytics.operations.drop(closeIndex + 1).any { operation ->
                operation == CrashOperation.Value("mini_app_visibility", "OBSCURED") ||
                    operation is CrashOperation.Message &&
                    operation.value.startsWith("miniapp_visibility_changed")
            },
        )
    }

    @Test
    fun active_close_is_delivered_once_and_stale_close_is_ignored() = runTest {
        val setup = build()
        setup.launchAndCreate(FIRST_ID, componentContext())
        val staleHost = setup.firstPlugin.hosts.single()

        staleHost.close()
        staleHost.close()
        runCurrent()

        assertEquals(1, setup.closeCalls)
        assertEquals(1, setup.crashlytics.messages.count { it.startsWith("miniapp_session_closed") })
        setup.coordinator.createSessionWithChildScope(SECOND_ID, MiniAppSessionKey(2), componentContext())
        val snapshot = setup.crashlytics.operations.toList()
        staleHost.close()
        runCurrent()
        assertEquals(snapshot, setup.crashlytics.operations)
        assertEquals(1, setup.closeCalls)
    }

    @Test
    fun reentrant_crashlytics_replacement_makes_close_stale_before_navigation() = runTest {
        val crashlytics = ReentrantCrashlytics()
        val setup = build(crashlytics = crashlytics)
        setup.launchAndCreate(FIRST_ID, componentContext())
        val oldHost = setup.firstPlugin.hosts.single()
        val replacementContext = componentContext()
        crashlytics.onMessage = { message ->
            if (message.startsWith("miniapp_session_closed")) {
                setup.coordinator.createSessionWithChildScope(
                    SECOND_ID,
                    MiniAppSessionKey(2),
                    replacementContext,
                )
            }
        }

        oldHost.close()
        runCurrent()

        assertEquals(0, setup.closeCalls)
        assertEquals(1, setup.secondPlugin.createCount)
        assertEquals(SECOND_ID.value, crashlytics.recording.values["mini_app_id"])
    }

    @Test
    fun active_review_reserves_and_opens_sheet_while_stale_review_is_ignored() = runTest {
        val setup = build()
        setup.launchAndCreate(FIRST_ID, componentContext())
        val staleHost = setup.firstPlugin.hosts.single()
        val opportunity = MiniAppReviewOpportunity("result", score = 9, bestScore = 12, revivesUsed = 1)

        staleHost.requestReview(opportunity)
        runCurrent()
        assertEquals(listOf(FIRST_ID to opportunity), setup.reviews)
        assertEquals(1, setup.reviewPolicy.acquireCalls)

        setup.coordinator.createSessionWithChildScope(SECOND_ID, MiniAppSessionKey(2), componentContext())
        val snapshot = setup.crashlytics.operations.toList()
        staleHost.requestReview(MiniAppReviewOpportunity("stale"))
        runCurrent()
        assertEquals(1, setup.reviewPolicy.acquireCalls)
        assertEquals(snapshot, setup.crashlytics.operations)

        lateinit var changedDuringAcquire: Setup
        val policy = RecordingReviewPolicy(
            afterAcquire = { changedDuringAcquire.coordinator.setObscured(true) },
        )
        changedDuringAcquire = build(reviewPolicy = policy)
        changedDuringAcquire.launchAndCreate(FIRST_ID, componentContext())
        changedDuringAcquire.firstPlugin.hosts.single().requestReview(opportunity)
        runCurrent()
        assertEquals(emptyList(), changedDuringAcquire.reviews)
        assertEquals(1, policy.releaseCalls)
    }

    @Test
    fun restored_key_advances_generator_and_old_callbacks_cannot_mutate_new_context() = runTest {
        val setup = build()
        val restoredLifecycle = lifecycle()
        restoredLifecycle.resume()
        setup.coordinator.createSessionWithChildScope(
            FIRST_ID,
            MiniAppSessionKey(41),
            DefaultComponentContext(restoredLifecycle),
        )
        val staleHost = setup.firstPlugin.hosts.single()
        restoredLifecycle.destroy()
        var nextKey: MiniAppSessionKey? = null

        setup.coordinator.launch(SECOND_ID) { key ->
            nextKey = key
            setup.coordinator.createSessionWithChildScope(SECOND_ID, key, componentContext())
        }
        assertEquals(MiniAppSessionKey(42), nextKey)
        assertEquals(SECOND_ID.value, setup.crashlytics.values["mini_app_id"])
        val snapshot = setup.crashlytics.operations.toList()

        staleHost.close()
        staleHost.requestReview(MiniAppReviewOpportunity("stale"))
        runCurrent()

        assertEquals(snapshot, setup.crashlytics.operations)
        assertEquals(SECOND_ID.value, setup.crashlytics.values["mini_app_id"])
        assertEquals(0, setup.closeCalls)
        assertEquals(0, setup.reviewPolicy.acquireCalls)
    }

    @Test
    fun crashlytics_facade_failure_cannot_break_session_creation_or_navigation() {
        val setup = build(crashlytics = ThrowingCrashlytics())
        var state: RootComponent.MiniAppState? = null

        setup.coordinator.launch(FIRST_ID) { key ->
            state = setup.coordinator.createSessionWithChildScope(FIRST_ID, key, componentContext())
        }

        assertIs<RootComponent.MiniAppState.Content>(state)
        assertEquals(1, setup.firstPlugin.createCount)
    }

    @Test
    fun cancellation_from_session_factory_is_rethrown_and_not_reported() {
        val cancellation = CancellationException("cancelled")
        val setup = build(firstPlugin = RecordingPlugin(FIRST_ID, cancellation))
        val failedContext = componentContext()
        val failedScope = failedContext.coroutineScope()
        var callbackInvoked = false

        val thrown = assertFailsWith<CancellationException> {
            setup.coordinator.launch(FIRST_ID) { key ->
                callbackInvoked = true
                setup.coordinator.createSession(
                    id = FIRST_ID,
                    key = key,
                    componentContext = failedContext,
                    scope = failedScope,
                )
            }
        }

        assertSame(cancellation, thrown)
        assertEquals(true, callbackInvoked)
        assertFalse(failedScope.isActive)
        assertEquals("", setup.crashlytics.values["mini_app_id"])
        assertEquals("", setup.crashlytics.values["mini_app_session_key"])
        assertEquals("", setup.crashlytics.values["mini_app_visibility"])
        assertEquals("closed", setup.crashlytics.values["mini_app_state"])
        assertEquals(emptyList(), setup.crashlytics.exceptions)
        assertEquals(emptyList(), setup.analytics.events)

        var replacementState: RootComponent.MiniAppState? = null
        setup.coordinator.launch(SECOND_ID) { key ->
            replacementState = setup.coordinator.createSessionWithChildScope(
                SECOND_ID,
                key,
                componentContext(),
            )
        }
        assertIs<RootComponent.MiniAppState.Content>(replacementState)
        assertEquals(1, setup.secondPlugin.createCount)
    }

    private fun build(
        firstPlugin: RecordingPlugin = RecordingPlugin(FIRST_ID),
        crashlytics: CrashlyticsRepository = RecordingCrashlytics(),
        reviewPolicy: RecordingReviewPolicy = RecordingReviewPolicy(),
        afterClose: () -> Unit = {},
    ): Setup {
        val secondPlugin = RecordingPlugin(SECOND_ID)
        val registry = RecordingRegistry(firstPlugin, secondPlugin)
        val analytics = RecordingAnalytics()
        var closeCalls = 0
        val reviews = mutableListOf<Pair<MiniAppId, MiniAppReviewOpportunity>>()
        val coordinator = MiniAppRuntimeCoordinator(
            registry = registry,
            reviewPolicy = reviewPolicy,
            analytics = analytics,
            crashlytics = crashlytics,
            initialForeground = true,
            closeActiveSession = {
                closeCalls += 1
                afterClose()
            },
            showReview = { id, opportunity ->
                reviews += id to opportunity
                true
            },
        )
        return Setup(
            coordinator = coordinator,
            registry = registry,
            firstPlugin = firstPlugin,
            secondPlugin = secondPlugin,
            reviewPolicy = reviewPolicy,
            analytics = analytics,
            crashlytics = crashlytics as? RecordingCrashlytics ?: RecordingCrashlytics(),
            closeCallsProvider = { closeCalls },
            reviews = reviews,
        )
    }

    private fun componentContext(): ComponentContext = DefaultComponentContext(lifecycle())

    private fun lifecycle(): LifecycleRegistry = LifecycleRegistry().also(lifecycles::add)

    private data class Setup(
        val coordinator: MiniAppRuntimeCoordinator,
        val registry: RecordingRegistry,
        val firstPlugin: RecordingPlugin,
        val secondPlugin: RecordingPlugin,
        val reviewPolicy: RecordingReviewPolicy,
        val analytics: RecordingAnalytics,
        val crashlytics: RecordingCrashlytics,
        val closeCallsProvider: () -> Int,
        val reviews: List<Pair<MiniAppId, MiniAppReviewOpportunity>>,
    ) {
        val closeCalls: Int get() = closeCallsProvider()

        fun launchAndCreate(id: MiniAppId, componentContext: ComponentContext) {
            coordinator.launch(id) { key ->
                coordinator.createSessionWithChildScope(id, key, componentContext)
            }
        }
    }

    private sealed interface CrashOperation {
        data class Value(val key: String, val value: Any) : CrashOperation
        data class Message(val value: String) : CrashOperation
        data class Exception(val value: Throwable) : CrashOperation
    }

    private class RecordingCrashlytics : CrashlyticsRepository {
        val operations = mutableListOf<CrashOperation>()
        val messages: List<String> get() = operations.filterIsInstance<CrashOperation.Message>().map { it.value }
        val exceptions: List<Throwable> get() = operations.filterIsInstance<CrashOperation.Exception>().map { it.value }
        val values: Map<String, Any>
            get() = operations.filterIsInstance<CrashOperation.Value>().associate { it.key to it.value }

        override fun setCustomValue(key: String, value: Any) {
            operations += CrashOperation.Value(key, value)
        }

        override fun logMessage(message: String) {
            operations += CrashOperation.Message(message)
        }

        override fun logException(throwable: Throwable) {
            operations += CrashOperation.Exception(throwable)
        }

        override fun setUserID(id: String) = Unit
        override fun clearUserID() = Unit
    }

    private class ThrowingCrashlytics : CrashlyticsRepository {
        override fun setCustomValue(key: String, value: Any): Unit = error("setCustomValue")
        override fun logMessage(message: String): Unit = error("logMessage")
        override fun logException(throwable: Throwable): Unit = error("logException")
        override fun setUserID(id: String): Unit = error("setUserID")
        override fun clearUserID(): Unit = error("clearUserID")
    }

    private class ReentrantCrashlytics : CrashlyticsRepository {
        val recording = RecordingCrashlytics()
        var onMessage: (String) -> Unit = {}

        override fun setCustomValue(key: String, value: Any) = recording.setCustomValue(key, value)

        override fun logMessage(message: String) {
            recording.logMessage(message)
            onMessage(message)
        }

        override fun logException(throwable: Throwable) = recording.logException(throwable)
        override fun setUserID(id: String) = Unit
        override fun clearUserID() = Unit
    }

    private class RecordingRegistry(vararg registered: RecordingPlugin) : MiniAppRegistry {
        private val plugins = registered.associateBy { it.manifest.id }
        override val manifests = registered.map { it.manifest }
        val lookups = mutableListOf<MiniAppId>()

        override fun get(id: MiniAppId): MiniAppPlugin? {
            lookups += id
            return plugins[id]
        }
    }

    private class RecordingPlugin(
        id: MiniAppId,
        private val failure: Throwable? = null,
        private val closeOnVisibility: MiniAppVisibility? = null,
    ) : MiniAppPlugin {
        override val manifest = manifest(id)
        var createCount = 0
        val visibility = mutableListOf<MiniAppVisibilitySource>()
        val hosts = mutableListOf<MiniAppSessionHost>()

        override fun createSession(
            componentContext: ComponentContext,
            visibility: MiniAppVisibilitySource,
            host: MiniAppSessionHost,
        ): MiniAppSession {
            createCount += 1
            this.visibility += visibility
            hosts += host
            closeOnVisibility?.let { target ->
                componentContext.coroutineScope().launch(start = CoroutineStart.UNDISPATCHED) {
                    visibility.visibility.first { it == target }
                    host.close()
                }
            }
            failure?.let { throw it }
            return object : MiniAppSession {
                @Composable
                override fun Content(modifier: Modifier) = Unit
            }
        }
    }

    private class RecordingReviewPolicy(
        private val allow: Boolean = true,
        private val afterAcquire: () -> Unit = {},
    ) : AppReviewPolicy {
        var acquireCalls = 0
        var releaseCalls = 0

        override suspend fun tryAcquirePrompt(): Boolean {
            acquireCalls += 1
            if (allow) afterAcquire()
            return allow
        }

        override suspend fun releasePrompt() {
            releaseCalls += 1
        }
    }

    private class RecordingAnalytics : AnalyticRepository {
        val events = mutableListOf<Pair<String, Map<String, Any>?>>()
        override fun logEvent(eventName: String, params: Map<String, Any>?) {
            events += eventName to params
        }

        override fun deleteData() = Unit
    }

    companion object {
        private val FIRST_ID = MiniAppId("game.alpha")
        private val SECOND_ID = MiniAppId("game.beta")

        private fun manifest(id: MiniAppId) = MiniAppManifest(
            id = id,
            title = StringResource("test:${id.value}:title", "title", emptySet()),
            description = StringResource("test:${id.value}:description", "description", emptySet()),
            icon = DrawableResource("test:${id.value}:icon", emptySet()),
            cover = null,
            category = MiniAppCategoryId("game"),
            sortPriority = 0,
        )
    }
}

private fun MiniAppRuntimeCoordinator.createSessionWithChildScope(
    id: MiniAppId,
    key: MiniAppSessionKey,
    componentContext: ComponentContext,
): RootComponent.MiniAppState = createSession(
    id = id,
    key = key,
    componentContext = componentContext,
    scope = componentContext.coroutineScope(),
)
