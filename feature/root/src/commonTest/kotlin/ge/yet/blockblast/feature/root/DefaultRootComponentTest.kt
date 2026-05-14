package ge.yet.blockblast.feature.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import ge.yet.blockblast.feature.game.GameComponent
import ge.yet.blockblast.feature.home.HomeComponent
import ge.yet.blokblast.domain.model.FeedbackType
import ge.yet.blokblast.domain.repository.AudioRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultRootComponentTest {

    private fun build(): Setup {
        val lifecycle = LifecycleRegistry()
        val audio = RecordingAudio()
        val settings = FakeSettings()
        val homeFactory = RecordingHomeFactory()
        val gameFactory = RecordingGameFactory()
        val component = DefaultRootComponent(
            componentContext = DefaultComponentContext(lifecycle),
            homeFactory = homeFactory,
            gameFactory = gameFactory,
            audio = audio,
            settingsRepository = settings,
        )
        return Setup(component, lifecycle, audio, settings, homeFactory, gameFactory)
    }

    @Test
    fun initial_stack_is_home() {
        val (component, _, _, _, _) = build().destructure()
        assertIs<RootComponent.Child.Home>(component.stack.value.active.instance)
    }

    @Test
    fun darkTheme_vibration_sound_tutorial_flows_mirror_settings() {
        val (component, _, _, settings, _, _) = build()
        assertFalse(component.darkTheme.value)
        settings.darkFlow.value = true
        assertTrue(component.darkTheme.value)
        settings.soundFlow.value = false
        assertFalse(component.soundEnabled.value)
        settings.vibrationFlow.value = false
        assertFalse(component.vibrationEnabled.value)
        settings.tutorialFlow.value = true
        assertTrue(component.tutorialSeen.value)
    }

    @Test
    fun onTutorialSeen_persists_via_repository() = runTest {
        val (component, _, _, settings, _, _) = build()
        component.onTutorialSeen()
        assertTrue(settings.tutorialFlow.value)
    }

    @Test
    fun resume_lifecycle_calls_audio_onAppForeground() = runTest {
        val (_, lifecycle, audio, _, _, _) = build()
        lifecycle.resume()
        assertTrue(audio.foregroundCount >= 1)
    }

    @Test
    fun stop_lifecycle_calls_audio_onAppBackground() = runTest {
        val (_, lifecycle, audio, _, _, _) = build()
        lifecycle.resume()
        lifecycle.stop()
        assertTrue(audio.backgroundCount >= 1)
    }

    @Test
    fun home_continueClicked_navigates_to_game_with_isNewGame_false() {
        val (component, _, _, _, homeFactory, gameFactory) = build()
        homeFactory.created.first().onContinueClicked(false)
        val child = component.stack.value.active.instance
        assertIs<RootComponent.Child.Game>(child)
        assertEquals(listOf(false), gameFactory.requestedIsNewGame)
    }

    @Test
    fun home_newGameClicked_navigates_to_game_with_isNewGame_true() {
        val (component, _, _, _, homeFactory, gameFactory) = build()
        homeFactory.created.first().onNewGameClicked(true)
        val child = component.stack.value.active.instance
        assertIs<RootComponent.Child.Game>(child)
        assertEquals(listOf(true), gameFactory.requestedIsNewGame)
    }

    @Test
    fun onBackClicked_pops_back_to_home() {
        val (component, _, _, _, homeFactory, _) = build()
        homeFactory.created.first().onNewGameClicked(true)
        assertIs<RootComponent.Child.Game>(component.stack.value.active.instance)
        component.onBackClicked()
        assertIs<RootComponent.Child.Home>(component.stack.value.active.instance)
    }

    private fun Setup.destructure() = this

    private data class Setup(
        val component: DefaultRootComponent,
        val lifecycle: LifecycleRegistry,
        val audio: RecordingAudio,
        val settings: FakeSettings,
        val homeFactory: RecordingHomeFactory,
        val gameFactory: RecordingGameFactory,
    )

    // ── Fakes ────────────────────────────────────────────────────────────

    private class RecordingHomeFactory : HomeComponent.Factory {
        val created = mutableListOf<FakeHome>()
        override fun create(
            componentContext: ComponentContext,
            onContinueClicked: (Boolean) -> Unit,
            onNewGameClicked: (Boolean) -> Unit,
        ): HomeComponent = FakeHome(onContinueClicked, onNewGameClicked).also { created += it }
    }

    private class FakeHome(
        val onContinueClicked: (Boolean) -> Unit,
        val onNewGameClicked: (Boolean) -> Unit,
    ) : HomeComponent {
        override val model = com.arkivanov.decompose.value.MutableValue(
            HomeComponent.Model(bestScore = 0L, hasSavedGame = false),
        )
        override fun onContinueClicked() = onContinueClicked(false)
        override fun onNewGameClicked() = onNewGameClicked(true)
    }

    private class RecordingGameFactory : GameComponent.Factory {
        val requestedIsNewGame = mutableListOf<Boolean>()
        override fun create(
            componentContext: ComponentContext,
            isNewGame: Boolean,
            onExitClicked: () -> Unit,
        ): GameComponent {
            requestedIsNewGame += isNewGame
            return FakeGame()
        }
    }

    private class FakeGame : GameComponent {
        override val model = com.arkivanov.decompose.value.MutableValue(
            GameComponent.Model(
                game = ge.yet.blokblast.domain.model.GameState(),
                continueCountdown = -1,
            ),
        )
        override val sheetSlot = com.arkivanov.decompose.value.MutableValue(
            com.arkivanov.decompose.router.slot.ChildSlot<Any, GameComponent.SheetChild>(child = null),
        )
        override fun onCellClicked(pieceId: Long, x: Int, y: Int) {}
        override fun onReviveClicked() {}
        override fun onRestartClicked() {}
        override fun onSettingsClicked() {}
        override fun onExitClicked() {}
        override fun onDismissSheet() {}
    }

    private class RecordingAudio : AudioRepository {
        var foregroundCount = 0
        var backgroundCount = 0
        override suspend fun playPlacementSound() {}
        override suspend fun playClearSound(lines: Int) {}
        override suspend fun playVoiceFeedback(type: FeedbackType) {}
        override suspend fun playVoiceCombo(combo: Int) {}
        override suspend fun startMusic() {}
        override suspend fun stopMusic() {}
        override suspend fun onAppBackground() { backgroundCount += 1 }
        override suspend fun onAppForeground() { foregroundCount += 1 }
    }

    private class FakeSettings : SettingsRepository {
        val soundFlow = MutableStateFlow(true)
        val vibrationFlow = MutableStateFlow(true)
        val darkFlow = MutableStateFlow(false)
        val tutorialFlow = MutableStateFlow(false)
        override val soundEnabled = soundFlow.asStateFlow()
        override val vibrationEnabled = vibrationFlow.asStateFlow()
        override val darkTheme = darkFlow.asStateFlow()
        override val tutorialSeen = tutorialFlow.asStateFlow()
        override val bestScore = MutableStateFlow(0L).asStateFlow()
        override val reviewPromptCount = MutableStateFlow(0).asStateFlow()
        override suspend fun setSoundEnabled(enabled: Boolean) { soundFlow.value = enabled }
        override suspend fun setVibrationEnabled(enabled: Boolean) { vibrationFlow.value = enabled }
        override suspend fun setDarkTheme(enabled: Boolean) { darkFlow.value = enabled }
        override suspend fun setBestScore(score: Long) {}
        override suspend fun incrementReviewPromptCount() {}
        override suspend fun suppressReviewPrompts(max: Int) {}
        override suspend fun setTutorialSeen() { tutorialFlow.value = true }
    }
}
