package ge.yet.game.blockblast.ui.result

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.game.blockblast.component.result.GameResultComponent
import ge.yet.game.blockblast.generated.resources.Res
import ge.yet.game.blockblast.generated.resources.best
import ge.yet.game.blockblast.generated.resources.cd_advertisement
import ge.yet.game.blockblast.generated.resources.exit_to_home
import ge.yet.game.blockblast.generated.resources.game_over
import ge.yet.game.blockblast.generated.resources.game_over_subtitle
import ge.yet.game.blockblast.generated.resources.new_best
import ge.yet.game.blockblast.generated.resources.new_game
import ge.yet.game.blockblast.generated.resources.revive
import ge.yet.game.blockblast.generated.resources.score
import ge.yet.game.blockblast.ui.game.GameGrid
import ge.yet.game.blockblast.ui.game.rememberReducedMotion
import ge.yet.game.miniapp.compose.MiniAppInterstitialGate
import ge.yet.game.uikit.components.background.AmbientMeshBackground
import org.jetbrains.compose.resources.stringResource

@Composable
fun GameResultContent(
    component: GameResultComponent,
    interstitialGate: MiniAppInterstitialGate,
    modifier: Modifier = Modifier,
) {
    val model by component.model.subscribeAsState()
    val reducedMotion = rememberReducedMotion()

    GameResultContent(
        model = model,
        onPrimaryClicked = {
            component.onPrimaryClicked(interstitialGate.request)
        },
        onHomeClicked = component::onHomeClicked,
        reducedMotion = reducedMotion,
        willShowAd = interstitialGate.willShowAd,
        modifier = modifier,
    )

}

@Composable
internal fun GameResultContent(
    model: GameResultComponent.Model,
    onPrimaryClicked: () -> Unit,
    onHomeClicked: () -> Unit,
    reducedMotion: Boolean = false,
    willShowAd: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AmbientMeshBackground(
                modifier = Modifier.fillMaxSize(),
                baseColor = MaterialTheme.colorScheme.background,
                animated = resultAmbientMotionEnabled(reducedMotion),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val layoutPolicy = resultLayoutPolicy(
                        widthDp = maxWidth.value,
                        heightDp = maxHeight.value,
                    )
                    if (maxWidth > maxHeight) {
                        LandscapeResultLayout(
                            model = model,
                            layoutPolicy = layoutPolicy,
                            reducedMotion = reducedMotion,
                            willShowAd = willShowAd,
                            onPrimaryClicked = onPrimaryClicked,
                            onHomeClicked = onHomeClicked,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        PortraitResultLayout(
                            model = model,
                            layoutPolicy = layoutPolicy,
                            reducedMotion = reducedMotion,
                            willShowAd = willShowAd,
                            onPrimaryClicked = onPrimaryClicked,
                            onHomeClicked = onHomeClicked,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortraitResultLayout(
    model: GameResultComponent.Model,
    layoutPolicy: ResultLayoutPolicy,
    reducedMotion: Boolean,
    willShowAd: Boolean,
    onPrimaryClicked: () -> Unit,
    onHomeClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(
            horizontal = layoutPolicy.horizontalPaddingDp.dp,
            vertical = layoutPolicy.verticalPaddingDp.dp,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ResultTitle(
            layoutPolicy = layoutPolicy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(layoutPolicy.sectionSpacingDp.dp))
        ResultBoard(
            model = model,
            reducedMotion = reducedMotion,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        Spacer(Modifier.height(layoutPolicy.sectionSpacingDp.dp))
        ResultActions(
            model = model,
            layoutPolicy = layoutPolicy,
            willShowAd = willShowAd,
            onPrimaryClicked = onPrimaryClicked,
            onHomeClicked = onHomeClicked,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
        )
    }
}

@Composable
private fun LandscapeResultLayout(
    model: GameResultComponent.Model,
    layoutPolicy: ResultLayoutPolicy,
    reducedMotion: Boolean,
    willShowAd: Boolean,
    onPrimaryClicked: () -> Unit,
    onHomeClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(
            horizontal = layoutPolicy.horizontalPaddingDp.dp,
            vertical = layoutPolicy.verticalPaddingDp.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResultBoard(
            model = model,
            reducedMotion = reducedMotion,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        Spacer(Modifier.width(layoutPolicy.sectionSpacingDp.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ResultTitle(
                layoutPolicy = layoutPolicy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(layoutPolicy.sectionSpacingDp.dp))
            ResultActions(
                model = model,
                layoutPolicy = layoutPolicy,
                willShowAd = willShowAd,
                onPrimaryClicked = onPrimaryClicked,
                onHomeClicked = onHomeClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
            )
        }
    }
}

@Composable
private fun ResultTitle(
    layoutPolicy: ResultLayoutPolicy,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.game_over),
            style = when {
                layoutPolicy.isUltraCompact -> MaterialTheme.typography.headlineSmall
                layoutPolicy.isCompact -> MaterialTheme.typography.headlineMedium
                else -> MaterialTheme.typography.headlineLarge
            },
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(layoutPolicy.titleSpacingDp.dp))
        Text(
            text = stringResource(Res.string.game_over_subtitle),
            style = if (layoutPolicy.isUltraCompact) {
                MaterialTheme.typography.bodySmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultBoard(
    model: GameResultComponent.Model,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val boardSize = minOf(maxWidth, maxHeight, MAX_RESULT_BOARD_SIZE)
        GameGrid(
            grid = model.snapshot.finalGrid,
            selectedPiece = null,
            onCellTapped = { _, _ -> },
            modifier = Modifier.size(boardSize),
            interactive = false,
            reducedMotion = reducedMotion,
        )
    }
}

@Composable
private fun ResultActions(
    model: GameResultComponent.Model,
    layoutPolicy: ResultLayoutPolicy,
    willShowAd: Boolean,
    onPrimaryClicked: () -> Unit,
    onHomeClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ResultCard(
        model = model,
        scoreLabel = stringResource(Res.string.score),
        bestLabel = stringResource(Res.string.best),
        newBestLabel = stringResource(Res.string.new_best),
        continueLabel = stringResource(Res.string.revive),
        newGameLabel = stringResource(Res.string.new_game),
        homeLabel = stringResource(Res.string.exit_to_home),
        advertisementLabel = if (willShowAd) {
            stringResource(Res.string.cd_advertisement)
        } else {
            null
        },
        layoutPolicy = layoutPolicy,
        onPrimaryClicked = onPrimaryClicked,
        onHomeClicked = onHomeClicked,
        modifier = modifier,
    )
}

internal fun resultAmbientMotionEnabled(reducedMotion: Boolean): Boolean = !reducedMotion

private val MAX_RESULT_BOARD_SIZE: Dp = 420.dp
