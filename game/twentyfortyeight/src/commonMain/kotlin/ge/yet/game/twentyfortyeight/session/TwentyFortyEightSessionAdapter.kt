package ge.yet.game.twentyfortyeight.session

import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.twentyfortyeight.analytics.TwentyFortyEightAnalytics
import ge.yet.game.twentyfortyeight.audio.TwentyFortyEightAudioAdapter
import ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightDiagnostics
import ge.yet.game.twentyfortyeight.engine.ResultSnapshot
import ge.yet.game.twentyfortyeight.store.AnnouncementFact
import ge.yet.game.twentyfortyeight.store.FocusTarget
import ge.yet.game.twentyfortyeight.store.TwentyFortyEightStore.Label
import ge.yet.game.twentyfortyeight.store.UiErrorCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

internal interface SessionNavigation {
    fun navigateToResult(snapshot: ResultSnapshot)
    fun onNewGameCommitted(runOrdinal: Long)
}

internal interface SessionUiEffects {
    fun announce(fact: AnnouncementFact)
    fun requestFocus(target: FocusTarget)
    fun showError(code: UiErrorCode)
}

internal class TwentyFortyEightSessionAdapter(
    private val navigation: SessionNavigation,
    private val audio: TwentyFortyEightAudioAdapter,
    private val analytics: TwentyFortyEightAnalytics,
    private val diagnostics: TwentyFortyEightDiagnostics,
    private val host: MiniAppSessionHost,
    private val uiEffects: SessionUiEffects,
) {
    suspend fun collect(labels: Flow<Label>) {
        labels.collect { label ->
            when (label) {
                is Label.NavigateToResult -> navigation.navigateToResult(label.snapshot)
                is Label.NewGameCommitted -> navigation.onNewGameCommitted(label.runOrdinal)
                Label.AudioStart -> audio.start()
                is Label.AudioControlsChanged -> audio.updateControls(label.controls)
                is Label.Audio -> audio.play(label.event)
                is Label.Analytics -> analytics.log(label.fact)
                is Label.Review -> host.requestReview(label.opportunity)
                is Label.Announcement -> uiEffects.announce(label.message)
                is Label.Focus -> uiEffects.requestFocus(label.target)
                is Label.TransientError -> uiEffects.showError(label.code)
                is Label.Diagnostic -> diagnostics.record(label.failure)
            }
        }
    }
}
