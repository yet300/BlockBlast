package ge.yet.game.feature.root

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.arkivanov.essenty.statekeeper.StateKeeper
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.domain.repository.AudioRepository
import ge.yet.game.domain.repository.CrashlyticsRepository
import ge.yet.game.domain.repository.SettingsRepository
import ge.yet.game.feature.catalog.CatalogComponent
import ge.yet.game.feature.review.AppReviewComponent
import ge.yet.game.feature.review.policy.AppReviewPolicy
import ge.yet.game.feature.settings.SettingsComponent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, InternalResourceApi::class)
class DefaultRootComponentTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val setups = mutableListOf<Setup>()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() {
        try {
            setups.forEach(Setup::destroy)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun cold_root_starts_at_catalog_and_statekeeper_restores_running() {
        val stateKeeper = StateKeeperDispatcher()
        val first = build(stateKeeper = stateKeeper)
        assertIs<RootComponent.Child.Catalog>(first.component.stack.value.active.instance)
        first.play(FIRST_ID)
        val saved = stateKeeper.save()
        first.destroy()
        val restoredPlugin = RecordingPlugin(FIRST_ID)

        val restored = build(
            stateKeeper = StateKeeperDispatcher(saved),
            firstPlugin = restoredPlugin,
        )

        assertEquals(FIRST_ID, restored.running().id)
        assertIs<RootComponent.MiniAppState.Content>(restored.running().state)
        assertEquals(1, restoredPlugin.createCount)
        assertEquals(1, restored.component.stack.value.items.size)
    }

    @Test
    fun restored_running_key_advances_generator_and_stale_host_cannot_affect_new_session() = runTest {
        val stateKeeper = StateKeeperDispatcher()
        val first = build(stateKeeper = stateKeeper)
        first.play(FIRST_ID)
        val saved = stateKeeper.save()
        first.destroy()
        val restoredPlugin = RecordingPlugin(FIRST_ID)

        val restored = build(
            stateKeeper = StateKeeperDispatcher(saved),
            firstPlugin = restoredPlugin,
        )

        val staleHost = restoredPlugin.hosts.single()
        assertEquals(1, restoredPlugin.createCount)
        restored.component.onBackClicked()
        restored.play(SECOND_ID)
        val crashContext = restored.crashlytics.operations.toList()

        staleHost.close()
        staleHost.requestReview(MiniAppReviewOpportunity("stale"))
        runCurrent()

        assertEquals(crashContext, restored.crashlytics.operations)
        assertEquals(SECOND_ID, restored.running().id)
        assertEquals(1, restored.secondPlugin.createCount)
        assertEquals(0, restored.reviewPolicy.acquireCalls)
    }

    @Test
    fun exact_manifest_id_creates_one_running_session() {
        val setup = build()
        setup.lifecycle.resume()
        setup.play(FIRST_ID)

        val child = assertIs<RootComponent.Child.RunningMiniApp>(setup.component.stack.value.active.instance)
        assertEquals(FIRST_ID, child.id)
        assertIs<RootComponent.MiniAppState.Content>(child.state)
        assertEquals(listOf(FIRST_ID), setup.registry.lookups)
        assertEquals(1, setup.firstPlugin.createCount)
        assertEquals(MiniAppVisibility.ACTIVE, setup.firstPlugin.visibility.single().visibility.value)
    }

    @Test
    fun repeated_play_while_running_creates_no_second_session() {
        val setup = build()
        setup.play(FIRST_ID)
        setup.play(FIRST_ID)
        assertEquals(1, setup.firstPlugin.createCount)
        assertEquals(listOf(FIRST_ID), setup.registry.lookups)
    }

    @Test
    fun unknown_id_stays_catalog_and_logs_host_failure() {
        val setup = build()
        val missing = MiniAppId("game.missing")
        setup.play(missing)
        assertIs<RootComponent.Child.Catalog>(setup.component.stack.value.active.instance)
        assertEquals("miniapp_launch_missing", setup.analytics.events.single().first)
        assertEquals(mapOf("miniapp_id" to missing.value), setup.analytics.events.single().second)
    }

    @Test
    fun synchronous_factory_failure_creates_unavailable_state_without_arming_host() = runTest {
        val failure = IllegalStateException("broken")
        val failed = RecordingPlugin(FIRST_ID, failure = failure)
        val setup = build(firstPlugin = failed)
        setup.play(FIRST_ID)

        val child = assertIs<RootComponent.Child.RunningMiniApp>(setup.component.stack.value.active.instance)
        assertEquals(RootComponent.MiniAppState.Unavailable(FIRST_ID), child.state)
        failed.hosts.single().close()
        failed.hosts.single().requestReview(MiniAppReviewOpportunity("ignored"))
        runCurrent()
        assertIs<RootComponent.Child.RunningMiniApp>(setup.component.stack.value.active.instance)
        assertEquals(0, setup.reviewPolicy.acquireCalls)
        assertEquals("miniapp_launch_failed", setup.analytics.events.single().first)
        assertEquals("IllegalStateException", setup.analytics.events.single().second?.get("error"))
        assertSame(failure, setup.crashlytics.exceptions.single())
    }

    @Test
    fun toolbar_back_closes_running_session_and_destroys_child_once() {
        val setup = build()
        setup.play(FIRST_ID)
        setup.component.onBackClicked()
        assertIs<RootComponent.Child.Catalog>(setup.component.stack.value.active.instance)
        assertEquals(1, setup.firstPlugin.destroyCount)
    }

    @Test
    fun system_back_matches_toolbar_back_when_no_sheet_is_open() {
        val setup = build()
        setup.play(FIRST_ID)
        assertTrue(setup.backDispatcher.back())
        assertIs<RootComponent.Child.Catalog>(setup.component.stack.value.active.instance)
        assertEquals(1, setup.firstPlugin.destroyCount)
    }

    @Test
    fun system_back_dismisses_settings_before_closing_session() {
        val setup = build()
        setup.play(FIRST_ID)
        setup.component.onSettingsClicked()
        assertTrue(setup.backDispatcher.back())
        assertNull(setup.component.sheetSlot.value.child)
        assertIs<RootComponent.Child.RunningMiniApp>(setup.component.stack.value.active.instance)
        assertEquals(0, setup.firstPlugin.destroyCount)
        assertTrue(setup.backDispatcher.back())
        assertIs<RootComponent.Child.Catalog>(setup.component.stack.value.active.instance)
    }

    @Test
    fun system_back_delegates_nested_settings_before_dismissing_sheet_and_session() {
        val setup = build()
        setup.play(FIRST_ID)
        setup.component.onSettingsClicked()
        val settings = setup.settingsFactory.created.single()
        settings.openNested()

        assertTrue(setup.backDispatcher.back())
        assertFalse(settings.isNested)
        assertIs<RootComponent.SheetChild.Settings>(setup.component.sheetSlot.value.child?.instance)
        assertIs<RootComponent.Child.RunningMiniApp>(setup.component.stack.value.active.instance)

        assertTrue(setup.backDispatcher.back())
        assertNull(setup.component.sheetSlot.value.child)
        assertIs<RootComponent.Child.RunningMiniApp>(setup.component.stack.value.active.instance)

        assertTrue(setup.backDispatcher.back())
        assertIs<RootComponent.Child.Catalog>(setup.component.stack.value.active.instance)
    }

    @Test
    fun active_session_close_dismisses_its_overlay_and_destroys_once() = runTest {
        val setup = build()
        setup.play(FIRST_ID)
        setup.component.onSettingsClicked()
        setup.firstPlugin.hosts.single().close()
        runCurrent()
        assertNull(setup.component.sheetSlot.value.child)
        assertIs<RootComponent.Child.Catalog>(setup.component.stack.value.active.instance)
        assertEquals(1, setup.firstPlugin.destroyCount)
    }

    @Test
    fun settings_retains_the_same_session_instance() {
        val setup = build()
        setup.play(FIRST_ID)
        val session = assertIs<RootComponent.MiniAppState.Content>(setup.running().state).session
        setup.component.onSettingsClicked()
        setup.component.onDismissSheet()
        assertSame(session, assertIs<RootComponent.MiniAppState.Content>(setup.running().state).session)
        assertEquals(1, setup.firstPlugin.createCount)
    }

    @Test
    fun active_obscured_background_active_visibility_does_not_recreate_session() {
        val setup = build()
        setup.lifecycle.resume()
        setup.play(FIRST_ID)
        val visibility = setup.firstPlugin.visibility.single().visibility
        assertEquals(MiniAppVisibility.ACTIVE, visibility.value)
        setup.component.onSettingsClicked()
        assertEquals(MiniAppVisibility.OBSCURED, visibility.value)
        setup.component.onDismissSheet()
        assertEquals(MiniAppVisibility.ACTIVE, visibility.value)
        setup.lifecycle.stop()
        assertEquals(MiniAppVisibility.BACKGROUND, visibility.value)
        setup.lifecycle.resume()
        assertEquals(MiniAppVisibility.ACTIVE, visibility.value)
        assertEquals(1, setup.firstPlugin.createCount)
    }

    @Test
    fun root_lifecycle_and_sheet_changes_are_delegated_to_runtime_visibility_and_crash_context() {
        val setup = build()
        setup.lifecycle.resume()
        setup.play(FIRST_ID)
        val session = assertIs<RootComponent.MiniAppState.Content>(setup.running().state).session

        setup.component.onSettingsClicked()
        assertSame(session, assertIs<RootComponent.MiniAppState.Content>(setup.running().state).session)
        setup.lifecycle.stop()
        setup.component.onDismissSheet()
        setup.lifecycle.resume()

        assertSame(session, assertIs<RootComponent.MiniAppState.Content>(setup.running().state).session)
        assertEquals(1, setup.firstPlugin.createCount)
        assertEquals(
            listOf("ACTIVE", "OBSCURED", "BACKGROUND", "ACTIVE"),
            setup.crashlytics.valuesFor("mini_app_visibility"),
        )
    }

    @Test
    fun background_wins_over_an_open_overlay() {
        val setup = build()
        setup.lifecycle.resume()
        setup.play(FIRST_ID)
        val visibility = setup.firstPlugin.visibility.single().visibility
        setup.component.onSettingsClicked()
        setup.lifecycle.stop()
        assertEquals(MiniAppVisibility.BACKGROUND, visibility.value)
    }

    @Test
    fun repeated_close_is_idempotent() = runTest {
        val setup = build()
        setup.play(FIRST_ID)
        val host = setup.firstPlugin.hosts.single()
        host.close()
        host.close()
        runCurrent()
        assertIs<RootComponent.Child.Catalog>(setup.component.stack.value.active.instance)
        assertEquals(1, setup.firstPlugin.destroyCount)
    }

    @Test
    fun stale_close_from_destroyed_session_cannot_close_a_new_session() = runTest {
        val setup = build()
        setup.play(FIRST_ID)
        val staleHost = setup.firstPlugin.hosts.single()
        staleHost.close()
        runCurrent()
        setup.play(SECOND_ID)
        staleHost.close()
        runCurrent()
        assertEquals(SECOND_ID, setup.running().id)
        assertEquals(1, setup.secondPlugin.createCount)
    }

    @Test
    fun stale_review_is_ignored_and_active_review_gets_authoritative_plugin_id() = runTest {
        val setup = build()
        setup.play(FIRST_ID)
        val staleHost = setup.firstPlugin.hosts.single()
        staleHost.close()
        runCurrent()
        setup.play(SECOND_ID)
        staleHost.requestReview(MiniAppReviewOpportunity("stale", score = 1))
        setup.secondPlugin.hosts.single().requestReview(
            MiniAppReviewOpportunity("result", score = 8, bestScore = 13, revivesUsed = 2),
        )
        runCurrent()
        assertEquals(1, setup.reviewPolicy.acquireCalls)
        assertIs<RootComponent.SheetChild.AppReview>(setup.component.sheetSlot.value.child?.instance)
        assertEquals(
            mapOf(
                "mini_app_id" to SECOND_ID.value,
                "source" to "result",
                "score" to 8L,
                "best_score" to 13L,
                "revives_used" to 2,
            ),
            setup.reviewFactory.analyticsParams.single(),
        )
    }

    @Test
    fun canceled_review_reservation_is_released() = runTest {
        lateinit var setup: Setup
        val policy = RecordingReviewPolicy(onAcquired = { setup.component.onBackClicked() })
        setup = build(reviewPolicy = policy)
        setup.play(FIRST_ID)
        setup.firstPlugin.hosts.single().requestReview(MiniAppReviewOpportunity("result"))
        runCurrent()
        assertEquals(1, policy.acquireCalls)
        assertEquals(1, policy.releaseCalls)
        assertNull(setup.component.sheetSlot.value.child)
        assertIs<RootComponent.Child.Catalog>(setup.component.stack.value.active.instance)
    }

    @Test
    fun app_start_and_stop_still_forward_to_audio_without_recreating_session() = runTest {
        val setup = build()
        setup.lifecycle.resume()
        setup.play(FIRST_ID)
        setup.lifecycle.stop()
        setup.lifecycle.resume()
        runCurrent()
        assertEquals(1, setup.audio.backgroundCount)
        assertEquals(2, setup.audio.foregroundCount)
        assertEquals(1, setup.firstPlugin.createCount)
    }

    @Test
    fun root_back_handler_is_disabled_on_plain_catalog() {
        val setup = build()
        assertFalse(setup.backDispatcher.isEnabled)
        assertFalse(setup.backDispatcher.back())
        assertIs<RootComponent.Child.Catalog>(setup.component.stack.value.active.instance)
    }

    private fun build(
        stateKeeper: StateKeeper? = null,
        firstPlugin: RecordingPlugin = RecordingPlugin(FIRST_ID),
        reviewPolicy: RecordingReviewPolicy = RecordingReviewPolicy(),
    ): Setup {
        val lifecycle = LifecycleRegistry()
        val backDispatcher = BackDispatcher()
        val secondPlugin = RecordingPlugin(SECOND_ID)
        val registry = RecordingRegistry(firstPlugin, secondPlugin)
        val catalogFactory = RecordingCatalogFactory(registry.manifests)
        val reviewFactory = RecordingReviewFactory()
        val settingsFactory = RecordingSettingsFactory()
        val audio = RecordingAudio()
        val settings = FakeSettings()
        val analytics = RecordingAnalytics()
        val crashlytics = RecordingCrashlytics()
        val component = DefaultRootComponent(
            componentContext = DefaultComponentContext(lifecycle, stateKeeper, backHandler = backDispatcher),
            catalogFactory = catalogFactory,
            settingsFactory = settingsFactory,
            reviewFactory = reviewFactory,
            reviewPolicy = reviewPolicy,
            miniAppRegistry = registry,
            audio = audio,
            settingsRepository = settings,
            analytics = analytics,
            crashlytics = crashlytics,
        )
        return Setup(component, lifecycle, backDispatcher, catalogFactory, settingsFactory, registry,
            firstPlugin, secondPlugin, reviewFactory, reviewPolicy, audio, analytics, crashlytics).also(setups::add)
    }

    private data class Setup(
        val component: DefaultRootComponent,
        val lifecycle: LifecycleRegistry,
        val backDispatcher: BackDispatcher,
        val catalogFactory: RecordingCatalogFactory,
        val settingsFactory: RecordingSettingsFactory,
        val registry: RecordingRegistry,
        val firstPlugin: RecordingPlugin,
        val secondPlugin: RecordingPlugin,
        val reviewFactory: RecordingReviewFactory,
        val reviewPolicy: RecordingReviewPolicy,
        val audio: RecordingAudio,
        val analytics: RecordingAnalytics,
        val crashlytics: RecordingCrashlytics,
    ) {
        private var destroyed = false
        fun play(id: MiniAppId) = catalogFactory.onPlay(id)
        fun running(): RootComponent.Child.RunningMiniApp = assertIs(component.stack.value.active.instance)
        fun destroy() {
            if (!destroyed) {
                destroyed = true
                lifecycle.destroy()
            }
        }
    }

    private class RecordingCatalogFactory(private val manifests: List<MiniAppManifest>) : CatalogComponent.Factory {
        lateinit var onPlay: (MiniAppId) -> Unit
        override fun create(componentContext: ComponentContext, onPlay: (MiniAppId) -> Unit): CatalogComponent {
            this.onPlay = onPlay
            return object : CatalogComponent {
                override val model: Value<CatalogComponent.Model> = MutableValue(CatalogComponent.Model(manifests))
                override fun onPlayClicked(id: MiniAppId) = onPlay(id)
            }
        }
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

    private class RecordingPlugin(id: MiniAppId, private val failure: Throwable? = null) : MiniAppPlugin {
        override val manifest = manifest(id)
        var createCount = 0
        var destroyCount = 0
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
            componentContext.lifecycle.doOnDestroy { destroyCount += 1 }
            failure?.let { throw it }
            return object : MiniAppSession {
                @Composable override fun Content(modifier: Modifier) = Unit
            }
        }
    }

    private class RecordingSettingsFactory : SettingsComponent.Factory {
        val created = mutableListOf<RecordingSettingsComponent>()
        override fun create(componentContext: ComponentContext, onBackClicked: () -> Unit): SettingsComponent =
            RecordingSettingsComponent(componentContext, onBackClicked).also(created::add)
    }

    private class RecordingSettingsComponent(
        componentContext: ComponentContext,
        private val close: () -> Unit,
    ) : SettingsComponent {
        override val backHandler = componentContext.backHandler
        override val stack: Value<ChildStack<*, SettingsComponent.Child>> get() = error("not used")
        var isNested = false
            private set
        fun openNested() {
            isNested = true
        }
        override fun onBackClicked() {
            if (isNested) isNested = false else close()
        }
    }

    private class RecordingReviewFactory : AppReviewComponent.Factory {
        val analyticsParams = mutableListOf<Map<String, Any>>()
        override fun create(
            componentContext: ComponentContext,
            analyticsParams: Map<String, Any>,
            onCloseRequested: () -> Unit,
        ): AppReviewComponent {
            this.analyticsParams += analyticsParams
            return object : AppReviewComponent {
                override fun onDontShowAgainClicked() = onCloseRequested()
                override fun onLeaveFeedbackClicked() = onCloseRequested()
            }
        }
    }

    private class RecordingReviewPolicy(
        private val allow: Boolean = true,
        private val onAcquired: () -> Unit = {},
    ) : AppReviewPolicy {
        var acquireCalls = 0
        var releaseCalls = 0
        override suspend fun tryAcquirePrompt(): Boolean {
            acquireCalls += 1
            if (allow) onAcquired()
            return allow
        }
        override suspend fun releasePrompt() { releaseCalls += 1 }
    }

    private class RecordingAudio : AudioRepository {
        var foregroundCount = 0
        var backgroundCount = 0
        override suspend fun onAppForeground() { foregroundCount += 1 }
        override suspend fun onAppBackground() { backgroundCount += 1 }
        override suspend fun playSound(filename: String) = Unit
        override suspend fun startMusic(tracks: List<String>) = Unit
        override suspend fun stopMusic() = Unit
    }

    private class FakeSettings : SettingsRepository {
        override val musicEnabled = MutableStateFlow(true)
        override val sfxEnabled = MutableStateFlow(true)
        override val vibrationEnabled = MutableStateFlow(true)
        override val darkTheme = MutableStateFlow(false)
        override val adsEnabled = MutableStateFlow(true)
        override suspend fun setMusicEnabled(enabled: Boolean) { musicEnabled.value = enabled }
        override suspend fun setSfxEnabled(enabled: Boolean) { sfxEnabled.value = enabled }
        override suspend fun setVibrationEnabled(enabled: Boolean) { vibrationEnabled.value = enabled }
        override suspend fun setDarkTheme(enabled: Boolean) { darkTheme.value = enabled }
        override suspend fun setAdsEnabled(enabled: Boolean) { adsEnabled.value = enabled }
    }

    private class RecordingAnalytics : AnalyticRepository {
        val events = mutableListOf<Pair<String, Map<String, Any>?>>()
        override fun logEvent(eventName: String, params: Map<String, Any>?) { events += eventName to params }
        override fun deleteData() = Unit
    }

    private sealed interface CrashOperation {
        data class Value(val key: String, val value: Any) : CrashOperation
        data class Message(val value: String) : CrashOperation
        data class Exception(val value: Throwable) : CrashOperation
    }

    private class RecordingCrashlytics : CrashlyticsRepository {
        val operations = mutableListOf<CrashOperation>()
        val exceptions: List<Throwable>
            get() = operations.filterIsInstance<CrashOperation.Exception>().map { it.value }

        fun valuesFor(key: String): List<Any> =
            operations.filterIsInstance<CrashOperation.Value>()
                .filter { it.key == key }
                .map { it.value }

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
