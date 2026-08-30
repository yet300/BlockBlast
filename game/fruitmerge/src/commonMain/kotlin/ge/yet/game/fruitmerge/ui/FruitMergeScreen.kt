package ge.yet.game.fruitmerge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.engine.RunPhase
import ge.yet.game.fruitmerge.engine.TargetingMode
import ge.yet.game.fruitmerge.generated.resources.Res
import ge.yet.game.fruitmerge.generated.resources.ad_disclosure
import ge.yet.game.fruitmerge.generated.resources.apple
import ge.yet.game.fruitmerge.generated.resources.best_score
import ge.yet.game.fruitmerge.generated.resources.blueberry
import ge.yet.game.fruitmerge.generated.resources.board_description
import ge.yet.game.fruitmerge.generated.resources.cancel
import ge.yet.game.fruitmerge.generated.resources.clear_hint
import ge.yet.game.fruitmerge.generated.resources.clear_with_ad
import ge.yet.game.fruitmerge.generated.resources.clear_with_count
import ge.yet.game.fruitmerge.generated.resources.cherry
import ge.yet.game.fruitmerge.generated.resources.danger_line
import ge.yet.game.fruitmerge.generated.resources.drop_hint
import ge.yet.game.fruitmerge.generated.resources.game_over
import ge.yet.game.fruitmerge.generated.resources.game_over_supporting
import ge.yet.game.fruitmerge.generated.resources.fruit_description
import ge.yet.game.fruitmerge.generated.resources.loading_game
import ge.yet.game.fruitmerge.generated.resources.mandarin
import ge.yet.game.fruitmerge.generated.resources.melon
import ge.yet.game.fruitmerge.generated.resources.new_game
import ge.yet.game.fruitmerge.generated.resources.next_fruit
import ge.yet.game.fruitmerge.generated.resources.peach
import ge.yet.game.fruitmerge.generated.resources.pear
import ge.yet.game.fruitmerge.generated.resources.pineapple
import ge.yet.game.fruitmerge.generated.resources.plum
import ge.yet.game.fruitmerge.generated.resources.score
import ge.yet.game.fruitmerge.generated.resources.shake_with_ad
import ge.yet.game.fruitmerge.generated.resources.shake_with_count
import ge.yet.game.fruitmerge.generated.resources.strawberry
import ge.yet.game.fruitmerge.session.FruitMergeComponent
import ge.yet.game.fruitmerge.session.PaidActionToken
import ge.yet.game.uikit.adaptive.AdaptiveGameScaffold
import ge.yet.game.uikit.components.button.PrimaryTerracottaButton
import ge.yet.game.uikit.components.button.SecondaryWarmSandButton
import ge.yet.game.uikit.components.card.RowCard
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal object FruitMergeTestTags {
    const val Viewport = "fruit_merge_viewport"
    const val Board = "fruit_merge_board"
    const val Support = "fruit_merge_support"
    const val Clear = "fruit_merge_clear"
    const val Shake = "fruit_merge_shake"
    const val NewGame = "fruit_merge_new_game"
}

@Composable
internal fun FruitMergeScreen(
    component: FruitMergeComponent,
    requestClearAd: (PaidActionToken) -> Unit,
    requestShakeAd: (PaidActionToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model by component.model.subscribeAsState()
    val reducedMotion = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor == 0f
    var faceTimeSeconds by remember(component) { mutableFloatStateOf(0f) }

    LaunchedEffect(
        component,
        model.initialized,
        model.visible,
        model.game.phase,
        reducedMotion,
    ) {
        if (!model.initialized || !model.visible || model.game.phase != RunPhase.PLAYING) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val elapsed = ((now - previous) / NANOS_PER_SECOND).coerceIn(0f, MAX_FRAME_SECONDS)
            previous = now
            component.frame(elapsed)
            if (!reducedMotion) faceTimeSeconds = (faceTimeSeconds + elapsed) % FACE_CLOCK_WRAP_SECONDS
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics { testTag = FruitMergeTestTags.Viewport },
        contentAlignment = Alignment.Center,
    ) {
        if (!model.initialized) {
            LoadingContent()
            return@Box
        }

        val game = model.game
        val boardDescription = stringResource(
            Res.string.board_description,
            game.score,
            game.bodies.size,
        )
        val dangerDescription = stringResource(Res.string.danger_line)

        AdaptiveGameScaffold(
            modifier = Modifier.fillMaxSize(),
            supportingPaneModifier = Modifier.semantics { testTag = FruitMergeTestTags.Support },
            primary = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FruitMergeBoard(
                        game = game,
                        faceTimeSeconds = faceTimeSeconds,
                        reducedMotion = reducedMotion,
                        boardDescription = boardDescription,
                        dangerDescription = dangerDescription,
                        onMovePreview = component::movePreview,
                        onDrop = component::drop,
                        onClearTarget = component::selectClearTarget,
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { testTag = FruitMergeTestTags.Board },
                    )
                    if (game.phase == RunPhase.RESULT) {
                        ResultCard(
                            score = game.score,
                            onNewGame = component::newGame,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            },
            supporting = {
                SupportingPanel(
                    game = game,
                    faceTimeSeconds = faceTimeSeconds,
                    reducedMotion = reducedMotion,
                    onClear = {
                        val token = component.requestClearGate()
                        if (token != null) requestClearAd(token)
                    },
                    onCancelClear = component::cancelClear,
                    onShake = {
                        val token = component.requestShakeGate()
                        if (token != null) requestShakeAd(token)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            },
        )
    }
}

@Composable
private fun LoadingContent() {
    val description = stringResource(Res.string.loading_game)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        CircularProgressIndicator()
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SupportingPanel(
    game: ge.yet.game.fruitmerge.engine.FruitMergeState,
    faceTimeSeconds: Float,
    reducedMotion: Boolean,
    onClear: () -> Unit,
    onCancelClear: () -> Unit,
    onShake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPlaying = game.phase == RunPhase.PLAYING
    val isTargeting = game.targetingMode == TargetingMode.CLEAR
    val scoreLabel = stringResource(Res.string.score)
    val bestLabel = stringResource(Res.string.best_score)
    val clearLabel = if (game.freeClears > 0) {
        stringResource(Res.string.clear_with_count, game.freeClears)
    } else {
        stringResource(Res.string.clear_with_ad)
    }
    val shakeLabel = if (game.freeShakes > 0) {
        stringResource(Res.string.shake_with_count, game.freeShakes)
    } else {
        stringResource(Res.string.shake_with_ad)
    }
    val advertisement = stringResource(Res.string.ad_disclosure)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RowCard(
                firstText = scoreLabel,
                secondText = game.score.toString(),
                modifier = Modifier.weight(1f),
            )
            RowCard(
                firstText = bestLabel,
                secondText = game.bestScore.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        NextFruitCard(
            level = game.previewLevel,
            faceTimeSeconds = faceTimeSeconds,
            reducedMotion = reducedMotion,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(if (isTargeting) Res.string.clear_hint else Res.string.drop_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (isTargeting) {
            SecondaryWarmSandButton(
                text = stringResource(Res.string.cancel),
                onClick = onCancelClear,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { testTag = FruitMergeTestTags.Clear },
            )
        } else {
            SecondaryWarmSandButton(
                text = clearLabel,
                onClick = onClear,
                enabled = isPlaying && game.bodies.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        testTag = FruitMergeTestTags.Clear
                        if (game.freeClears == 0) contentDescription = "$clearLabel, $advertisement"
                    },
            )
        }

        SecondaryWarmSandButton(
            text = shakeLabel,
            onClick = onShake,
            enabled = isPlaying && !isTargeting && game.bodies.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    testTag = FruitMergeTestTags.Shake
                    if (game.freeShakes == 0) contentDescription = "$shakeLabel, $advertisement"
                },
        )
    }
}

@Composable
private fun NextFruitCard(
    level: FruitLevel,
    faceTimeSeconds: Float,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val nextLabel = stringResource(Res.string.next_fruit)
    val fruitName = stringResource(fruitNameResource(level))
    val fruitDescription = stringResource(Res.string.fruit_description, fruitName)
    Card(
        modifier = modifier.semantics {
            contentDescription = "$nextLabel. $fruitDescription"
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = nextLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            FruitPreview(
                level = level,
                faceTimeSeconds = faceTimeSeconds,
                reducedMotion = reducedMotion,
                modifier = Modifier.size(58.dp),
            )
        }
    }
}

private fun fruitNameResource(level: FruitLevel): StringResource = when (level) {
    FruitLevel.BLUEBERRY -> Res.string.blueberry
    FruitLevel.CHERRY -> Res.string.cherry
    FruitLevel.STRAWBERRY -> Res.string.strawberry
    FruitLevel.PLUM -> Res.string.plum
    FruitLevel.MANDARIN -> Res.string.mandarin
    FruitLevel.APPLE -> Res.string.apple
    FruitLevel.PEAR -> Res.string.pear
    FruitLevel.PEACH -> Res.string.peach
    FruitLevel.PINEAPPLE -> Res.string.pineapple
    FruitLevel.MELON -> Res.string.melon
}

@Composable
private fun ResultCard(
    score: Long,
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth(0.84f)
            .clip(MaterialTheme.shapes.extraLarge),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.game_over),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.game_over_supporting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            PrimaryTerracottaButton(
                text = stringResource(Res.string.new_game),
                onClick = onNewGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { testTag = FruitMergeTestTags.NewGame },
            )
        }
    }
}

private const val NANOS_PER_SECOND: Float = 1_000_000_000f
private const val MAX_FRAME_SECONDS: Float = 0.05f
private const val FACE_CLOCK_WRAP_SECONDS: Float = 120f
