package ge.yet3.blokblast.screen.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.blockblast.feature.home.HomeComponent
import ge.yet3.blokblast.component.background.AmbientMeshBackground
import ge.yet3.blokblast.component.button.PrimaryTerracottaButton
import ge.yet3.blokblast.component.button.SecondaryWarmSandButton
import ge.yet3.blokblast.component.score.BestScoreCard
import ge.yet3.blokblast.theme.PieceColors
import org.jetbrains.compose.resources.stringResource
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.app_name
import blockblast.composeapp.generated.resources.best_score_label
import blockblast.composeapp.generated.resources.continue_game
import blockblast.composeapp.generated.resources.home_tagline
import blockblast.composeapp.generated.resources.new_game
import blockblast.composeapp.generated.resources.play

@Composable
fun HomeContent(component: HomeComponent) {
    val model by component.model.subscribeAsState()

    val fadeIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fadeIn.animateTo(1f, tween(500)) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(fadeIn.value),
        ) {
            AmbientMeshBackground(
                modifier = Modifier.fillMaxSize(),
                baseColor = MaterialTheme.colorScheme.background,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                GameLogo(title = stringResource(Res.string.app_name))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.home_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(48.dp))

                if (model.bestScore > 0L) {
                    BestScoreCard(
                        label = stringResource(Res.string.best_score_label),
                        bestScore = model.bestScore,
                    )
                    Spacer(Modifier.height(40.dp))
                } else {
                    Spacer(Modifier.height(24.dp))
                }

                PrimaryTerracottaButton(
                    text = if (model.hasSavedGame) {
                        stringResource(Res.string.continue_game)
                    } else {
                        stringResource(Res.string.play)
                    },
                    onClick = component::onPlayClicked,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (model.hasSavedGame) {
                    Spacer(Modifier.height(12.dp))
                    SecondaryWarmSandButton(
                        text = stringResource(Res.string.new_game),
                        onClick = component::onPlayClicked,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun GameLogo(title: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PieceColors.take(5).forEach { color ->
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(color),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = (-1).sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
