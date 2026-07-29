package ge.yet3.blokblast.screen.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.blockblast.feature.game.result.GameResultComponent
import ge.yet3.blokblast.ads.rememberGameOverInterstitial
import ge.yet3.blokblast.component.background.AmbientMeshBackground
import ge.yet3.blokblast.screen.game.GameGrid
import ge.yet3.blokblast.screen.game.rememberReducedMotion
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.best
import blockblast.composeapp.generated.resources.exit_to_home
import blockblast.composeapp.generated.resources.game_over
import blockblast.composeapp.generated.resources.game_over_subtitle
import blockblast.composeapp.generated.resources.new_best
import blockblast.composeapp.generated.resources.new_game
import blockblast.composeapp.generated.resources.revive
import blockblast.composeapp.generated.resources.score
import org.jetbrains.compose.resources.stringResource

@Composable
fun GameResultContent(
    component: GameResultComponent,
    modifier: Modifier = Modifier,
) {
    val model by component.model.subscribeAsState()
    val interstitial = rememberGameOverInterstitial()
    val reducedMotion = rememberReducedMotion()

    GameResultContent(
        model = model,
        onPrimaryClicked = {
            component.onPrimaryClicked(interstitial.show)
        },
        onHomeClicked = component::onHomeClicked,
        reducedMotion = reducedMotion,
        modifier = modifier,
    )
}

@Composable
fun GameResultContent(
    model: GameResultComponent.Model,
    onPrimaryClicked: () -> Unit,
    onHomeClicked: () -> Unit,
    reducedMotion: Boolean = false,
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
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(Res.string.game_over),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.game_over_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                GameGrid(
                    grid = model.snapshot.finalGrid,
                    selectedPiece = null,
                    onCellTapped = { _, _ -> },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                    reducedMotion = reducedMotion,
                )

                Spacer(Modifier.height(24.dp))

                ResultCard(
                    model = model,
                    scoreLabel = stringResource(Res.string.score),
                    bestLabel = stringResource(Res.string.best),
                    newBestLabel = stringResource(Res.string.new_best),
                    continueLabel = stringResource(Res.string.revive),
                    newGameLabel = stringResource(Res.string.new_game),
                    homeLabel = stringResource(Res.string.exit_to_home),
                    onPrimaryClicked = onPrimaryClicked,
                    onHomeClicked = onHomeClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                )
            }
        }
    }
}
