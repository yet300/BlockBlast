package ge.yet.game.twentyfortyeight.session

import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.miniapp.api.MiniAppReviewOpportunity
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.audio.AudioCommandResult
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioDuration
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.twentyfortyeight.analytics.AnalyticsFact
import ge.yet.game.twentyfortyeight.analytics.TwentyFortyEightAnalytics
import ge.yet.game.twentyfortyeight.audio.AudioEvent
import ge.yet.game.twentyfortyeight.audio.TwentyFortyEightAudioAdapter
import ge.yet.game.twentyfortyeight.diagnostics.InvariantCode
import ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightDiagnostics
import ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightFailure
import ge.yet.game.twentyfortyeight.engine.AudioControls
import ge.yet.game.twentyfortyeight.engine.GameStatistics
import ge.yet.game.twentyfortyeight.engine.ResultSnapshot
import ge.yet.game.twentyfortyeight.store.AnnouncementFact
import ge.yet.game.twentyfortyeight.store.FocusTarget
import ge.yet.game.twentyfortyeight.store.TwentyFortyEightStore.Label
import ge.yet.game.twentyfortyeight.store.UiErrorCode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TwentyFortyEightSessionAdapterTest {
    @Test
    fun `every Label routes once to its exact owner in source order`() = runTest {
        val routed = mutableListOf<String>()
        val controls = AudioControls(progress = 0.25f, danger = 0.5f, momentum = 0.75f)
        val snapshot = ResultSnapshot(32L, 64L, 8L, GameStatistics())
        val review = MiniAppReviewOpportunity("victory", score = 32L)
        val failure = TwentyFortyEightFailure.InvariantViolation(InvariantCode.IdentityOverflow)
        val labels = listOf(
            Label.NavigateToResult(snapshot),
            Label.NewGameCommitted(2L),
            Label.AudioStart,
            Label.AudioControlsChanged(controls),
            Label.Audio(AudioEvent.TileSpawn),
            Label.Analytics(AnalyticsFact.GameStarted(2L)),
            Label.Review(review),
            Label.Announcement(AnnouncementFact.Victory),
            Label.Focus(FocusTarget.Board),
            Label.TransientError(UiErrorCode.ProgressNotSaved),
            Label.Diagnostic(failure),
        )
        val adapter = TwentyFortyEightSessionAdapter(
            navigation = object : SessionNavigation {
                override fun navigateToResult(snapshot: ResultSnapshot) { routed += "result:$snapshot" }
                override fun onNewGameCommitted(runOrdinal: Long) { routed += "new:$runOrdinal" }
            },
            audio = TwentyFortyEightAudioAdapter(RecordingAudio(routed)),
            analytics = TwentyFortyEightAnalytics(object : AnalyticRepository {
                override fun logEvent(eventName: String, params: Map<String, Any>?) { routed += "analytics:$eventName" }
                override fun deleteData() = Unit
            }),
            diagnostics = TwentyFortyEightDiagnostics { routed += "diagnostic:$it" },
            host = object : MiniAppSessionHost {
                override fun close() = Unit
                override fun requestReview(opportunity: MiniAppReviewOpportunity) { routed += "review:$opportunity" }
            },
            uiEffects = object : SessionUiEffects {
                override fun announce(fact: AnnouncementFact) { routed += "announce:$fact" }
                override fun requestFocus(target: FocusTarget) { routed += "focus:$target" }
                override fun showError(code: UiErrorCode) { routed += "error:$code" }
            },
        )
        val flow = MutableSharedFlow<Label>(extraBufferCapacity = labels.size)
        val job = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { adapter.collect(flow) }

        labels.forEach { flow.emit(it) }
        runCurrent()

        assertEquals(
            listOf(
                "result:$snapshot",
                "new:2",
                "audio:start",
                "audio:control:progress:0.25",
                "audio:control:danger:0.5",
                "audio:control:momentum:0.75",
                "audio:sfx:tile_spawn",
                "analytics:game_started",
                "review:$review",
                "announce:${AnnouncementFact.Victory}",
                "focus:${FocusTarget.Board}",
                "error:${UiErrorCode.ProgressNotSaved}",
                "diagnostic:$failure",
            ),
            routed,
        )
        job.cancel()
    }

    private class RecordingAudio(private val routed: MutableList<String>) : MiniAppAudio {
        override fun playMusic(program: AudioProgram): AudioCommandResult =
            AudioCommandResult.Accepted.also { routed += "audio:start" }

        override fun stopMusic(fadeOut: AudioDuration): AudioCommandResult = AudioCommandResult.Accepted

        override fun playSfx(program: AudioProgram, name: SfxName): AudioCommandResult =
            AudioCommandResult.Accepted.also { routed += "audio:sfx:${name.value}" }

        override fun setControl(name: AudioControlName, value: Float): AudioCommandResult =
            AudioCommandResult.Accepted.also { routed += "audio:control:${name.value}:$value" }
    }
}
