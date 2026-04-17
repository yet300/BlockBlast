package ge.yet3.blokblast.component.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ge.yet3.blokblast.component.button.PrimaryTerracottaButton
import ge.yet3.blokblast.component.button.SecondaryWarmSandButton
import ge.yet3.blokblast.component.modifier.ringShadow
import ge.yet3.blokblast.component.modifier.whisperShadow
import ge.yet3.blokblast.component.score.AnimatedCounter
import ge.yet3.blokblast.utils.formatScore

/**
 * The end-of-round overlay.  Animates in with a custom dialog motion (spring
 * scale + soft slide + fade) per Sina Samaki's "custom dialog animation"
 * technique, and shows an animated stripe ribbon when the player just set a
 * new best score.
 */
@Composable
fun GameOverOverlay(
    visible: Boolean,
    score: Long,
    bestScore: Long,
    isNewBest: Boolean,
    canRevive: Boolean,
    onReviveClicked: () -> Unit,
    onRestartClicked: () -> Unit,
    onExitClicked: () -> Unit,
    title: String,
    subtitle: String,
    scoreLabel: String,
    bestLabel: String,
    newBestLabel: String,
    reviveLabel: String,
    restartLabel: String,
    exitLabel: String,
    /**
     * Seconds remaining on the Continue button. When `null` or ≤ 0 the primary
     * button morphs from "Continue (N)" into the restart action.
     */
    continueCountdownSeconds: Int? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180)),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(
                    initialScale = 0.86f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeIn(tween(260)) + slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { it / 6 },
                exit = scaleOut(targetScale = 0.92f, animationSpec = tween(180)) +
                    fadeOut(tween(160)) +
                    slideOutVertically(animationSpec = tween(180)) { it / 8 },
            ) {
                GameOverCard(
                    score = score,
                    bestScore = bestScore,
                    isNewBest = isNewBest,
                    canRevive = canRevive,
                    onReviveClicked = onReviveClicked,
                    onRestartClicked = onRestartClicked,
                    onExitClicked = onExitClicked,
                    title = title,
                    subtitle = subtitle,
                    scoreLabel = scoreLabel,
                    bestLabel = bestLabel,
                    newBestLabel = newBestLabel,
                    reviveLabel = reviveLabel,
                    restartLabel = restartLabel,
                    exitLabel = exitLabel,
                    continueCountdownSeconds = continueCountdownSeconds,
                )
            }
        }
    }
}

@Composable
private fun GameOverCard(
    score: Long,
    bestScore: Long,
    isNewBest: Boolean,
    canRevive: Boolean,
    onReviveClicked: () -> Unit,
    onRestartClicked: () -> Unit,
    onExitClicked: () -> Unit,
    title: String,
    subtitle: String,
    scoreLabel: String,
    bestLabel: String,
    newBestLabel: String,
    reviveLabel: String,
    restartLabel: String,
    exitLabel: String,
    continueCountdownSeconds: Int?,
) {
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier = Modifier
            .widthIn(min = 280.dp, max = 360.dp)
            .padding(horizontal = 24.dp)
            .whisperShadow(shape = shape, elevation = 32.dp)
            .ringShadow(
                color = MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .background(MaterialTheme.colorScheme.surface, shape)
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = scoreLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedCounter(
            value = score,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(8.dp))

        if (isNewBest) {
            NewBestRibbon(label = newBestLabel)
        } else if (bestScore > 0L) {
            Text(
                text = "$bestLabel · ${bestScore.formatScore()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(28.dp))

        // Primary button morphs based on the countdown:
        //   canRevive && countdown > 0  → "Continue (N)" → onReviveClicked
        //   canRevive && countdown <= 0 → restartLabel   → onRestartClicked
        //   !canRevive                  → primary button hidden; secondary restart shown
        val countdownActive = canRevive &&
            continueCountdownSeconds != null &&
            continueCountdownSeconds > 0
        val primaryActsAsRestart = canRevive && !countdownActive

        if (canRevive) {
            val primaryText = if (countdownActive) {
                "$reviveLabel ($continueCountdownSeconds)"
            } else {
                restartLabel
            }
            val primaryAction = if (countdownActive) onReviveClicked else onRestartClicked
            PrimaryTerracottaButton(
                text = primaryText,
                onClick = primaryAction,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }

        // Hide the secondary restart when the primary has already morphed into restart
        // (would be a duplicate button otherwise).
        if (!primaryActsAsRestart) {
            SecondaryWarmSandButton(
                text = restartLabel,
                onClick = onRestartClicked,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = exitLabel,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onExitClicked)
                .padding(vertical = 8.dp, horizontal = 12.dp),
        )
    }
}

/**
 * "New best score" ribbon — soft animated diagonal stripes scrolling slowly
 * across a Terracotta-tinted background, in the spirit of Sina Samaki's
 * "Animated Stripes" / "Fancy Ribbon" tutorials.
 */
@Composable
private fun NewBestRibbon(label: String) {
    val shape = RoundedCornerShape(12.dp)
    val transition = rememberInfiniteTransition(label = "new-best-stripes")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "stripe-phase",
    )

    val stripeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val stripeBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

    Box(
        modifier = Modifier
            .clip(shape)
            .background(stripeBg)
            .ringShadow(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.40f),
                shape = shape,
            )
            .drawBehind {
                val brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to stripeColor,
                        0.49f to stripeColor,
                        0.50f to Color.Transparent,
                        0.99f to Color.Transparent,
                    ),
                    start = Offset(-phase, -phase),
                    end = Offset(24f - phase, 24f - phase),
                    tileMode = TileMode.Repeated,
                )
                drawRect(brush)
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
