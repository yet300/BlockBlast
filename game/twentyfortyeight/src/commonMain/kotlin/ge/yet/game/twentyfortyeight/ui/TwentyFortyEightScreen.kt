package ge.yet.game.twentyfortyeight.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.game.twentyfortyeight.component.PlayingComponent
import ge.yet.game.twentyfortyeight.component.ResultComponent
import ge.yet.game.twentyfortyeight.session.TwentyFortyEightSessionComponent
import ge.yet.game.twentyfortyeight.store.AnnouncementFact
import ge.yet.game.twentyfortyeight.store.FocusTarget
import ge.yet.game.twentyfortyeight.store.UiErrorCode
import ge.yet.game.twentyfortyeight.generated.resources.Res
import ge.yet.game.twentyfortyeight.generated.resources.announcement_game_over
import ge.yet.game.twentyfortyeight.generated.resources.announcement_move
import ge.yet.game.twentyfortyeight.generated.resources.announcement_move_merge
import ge.yet.game.twentyfortyeight.generated.resources.announcement_new_best
import ge.yet.game.twentyfortyeight.generated.resources.announcement_victory
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TwentyFortyEightScreen(
    component: TwentyFortyEightSessionComponent,
    modifier: Modifier = Modifier,
) {
    val stack by component.stack.subscribeAsState()
    val effectState by component.effect.subscribeAsState()
    var error by remember(component) { mutableStateOf<UiErrorCode?>(null) }
    var announcement by remember(component) {
        mutableStateOf<TwentyFortyEightSessionComponent.Effect.Announcement?>(null)
    }
    val boardFocusRequester = remember(component) { FocusRequester() }
    val victoryFocusRequester = remember(component) { FocusRequester() }
    val resultFocusRequester = remember(component) { FocusRequester() }
    val effect = effectState.effect
    LaunchedEffect(effect?.id) {
        when (effect) {
            is TwentyFortyEightSessionComponent.Effect.Announcement -> {
                announcement = null
                withFrameNanos { }
                announcement = effect
            }
            is TwentyFortyEightSessionComponent.Effect.Focus -> {
                withFrameNanos { }
                when (effect.target) {
                    FocusTarget.Board -> boardFocusRequester
                    FocusTarget.Victory -> victoryFocusRequester
                    FocusTarget.Result -> resultFocusRequester
                }.requestFocus()
            }
            is TwentyFortyEightSessionComponent.Effect.Error -> error = effect.code
            null -> Unit
        }
        effect?.let { component.onEffectConsumed(it.id) }
    }

    AnnouncementLiveRegion(announcement)
    when (val instance = stack.active.instance) {
        is TwentyFortyEightSessionComponent.Child.Playing -> PlayingRoute(
            component = instance.component,
            error = error,
            boardFocusRequester = boardFocusRequester,
            victoryFocusRequester = victoryFocusRequester,
            modifier = modifier.fillMaxSize(),
        )
        is TwentyFortyEightSessionComponent.Child.Result -> ResultRoute(
            component = instance.component,
            error = error,
            resultFocusRequester = resultFocusRequester,
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PlayingRoute(
    component: PlayingComponent,
    error: UiErrorCode?,
    boardFocusRequester: FocusRequester,
    victoryFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val model by component.model.subscribeAsState()
    val overlay by component.overlay.subscribeAsState()

    PlayingContent(
        model = model,
        onDirection = component::onMove,
        onUndo = component::onUndoRequested,
        onRestart = component::onRestartRequested,
        onStatistics = component::onStatisticsRequested,
        onSkipTutorial = component::onTutorialSkipped,
        onTransitionCompleted = component::onAnimationCompleted,
        boardFocusRequester = boardFocusRequester,
        modifier = modifier,
        error = error,
    )
    overlay.child?.instance?.let { child ->
        OverlayContent(child, victoryFocusRequester = victoryFocusRequester)
    }
}

@Composable
private fun ResultRoute(
    component: ResultComponent,
    error: UiErrorCode?,
    resultFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val model by component.model.subscribeAsState()
    ResultContent(
        model = model,
        onNewGame = component::onNewGameRequested,
        modifier = modifier,
        error = error,
        resultFocusRequester = resultFocusRequester,
    )
}

@Composable
private fun AnnouncementLiveRegion(
    effect: TwentyFortyEightSessionComponent.Effect.Announcement?,
) {
    val text = effect?.let { announcementText(it.fact) } ?: return
    Box(
        modifier = Modifier
            .size(1.dp)
            .clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = text
            },
    )
}

@Composable
private fun announcementText(fact: AnnouncementFact): String = when (fact) {
    is AnnouncementFact.Move -> if (fact.largestMerge == null) {
        stringResource(Res.string.announcement_move, fact.scoreDelta.toString())
    } else {
        stringResource(
            Res.string.announcement_move_merge,
            fact.scoreDelta.toString(),
            fact.largestMerge.toString(),
        )
    }
    is AnnouncementFact.NewBest ->
        stringResource(Res.string.announcement_new_best, fact.value.toString())
    AnnouncementFact.Victory -> stringResource(Res.string.announcement_victory)
    AnnouncementFact.GameOver -> stringResource(Res.string.announcement_game_over)
}
