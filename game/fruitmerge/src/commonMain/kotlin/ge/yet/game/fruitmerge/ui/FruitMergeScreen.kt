package ge.yet.game.fruitmerge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
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
import ge.yet.game.fruitmerge.generated.resources.clear_with_ad
import ge.yet.game.fruitmerge.generated.resources.clear_with_count
import ge.yet.game.fruitmerge.generated.resources.cherry
import ge.yet.game.fruitmerge.generated.resources.danger_line
import ge.yet.game.fruitmerge.generated.resources.loading_game
import ge.yet.game.fruitmerge.generated.resources.mandarin
import ge.yet.game.fruitmerge.generated.resources.melon
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
import ge.yet.game.uikit.components.button.IconCircleButton
import ge.yet.game.uikit.components.icon.BombFilled
import ge.yet.game.uikit.components.icon.Crown
import ge.yet.game.uikit.components.icon.Vibration
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal object FruitMergeTestTags {
    const val Viewport = "fruit_merge_viewport"
    const val Board = "fruit_merge_board"
    const val Support = "fruit_merge_support"
    const val Clear = "fruit_merge_clear"
    const val Shake = "fruit_merge_shake"
    const val Evolution = "fruit_merge_evolution"
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

    LaunchedEffect(component, model.initialized, model.visible, model.game.phase, reducedMotion) {
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

    val game = model.game
    val dropEnabled = model.initialized && model.tutorialReady &&
        game.phase == RunPhase.PLAYING && game.targetingMode == TargetingMode.NONE
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .fruitMergeDropInput(
                enabled = dropEnabled,
                onMovePreview = component::movePreview,
                onDrop = component::drop,
            )
            .semantics { testTag = FruitMergeTestTags.Viewport },
        contentAlignment = Alignment.Center,
    ) {
        if (!model.initialized || !model.tutorialReady) {
            LoadingContent()
            return@Box
        }

        val boardDescription = stringResource(Res.string.board_description, game.score, game.bodies.size)
        val dangerDescription = stringResource(Res.string.danger_line)
        AdaptiveGameScaffold(
            modifier = Modifier.fillMaxSize(),
            supportingPaneModifier = Modifier.semantics { testTag = FruitMergeTestTags.Support },
            primary = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    FruitMergeBoard(
                        game = game,
                        faceTimeSeconds = faceTimeSeconds,
                        reducedMotion = reducedMotion,
                        boardDescription = boardDescription,
                        dangerDescription = dangerDescription,
                        onClearTarget = component::selectClearTarget,
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { testTag = FruitMergeTestTags.Board },
                    )
                    ActionHud(
                        freeClears = game.freeClears,
                        freeShakes = game.freeShakes,
                        hasBodies = game.bodies.isNotEmpty(),
                        isTargeting = game.targetingMode == TargetingMode.CLEAR,
                        onClear = {
                            val token = component.requestClearGate()
                            if (token != null) requestClearAd(token)
                        },
                        onCancelClear = component::cancelClear,
                        onShake = {
                            val token = component.requestShakeGate()
                            if (token != null) requestShakeAd(token)
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                    )
                }
            },
            supporting = {
                SupportingPanel(
                    score = game.score,
                    bestScore = game.bestScore,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                )
            },
        )
    }
}

@Composable
private fun LoadingContent() {
    val description = stringResource(Res.string.loading_game)
    CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = description })
}

@Composable
private fun ActionHud(
    freeClears: Int,
    freeShakes: Int,
    hasBodies: Boolean,
    isTargeting: Boolean,
    onClear: () -> Unit,
    onCancelClear: () -> Unit,
    onShake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clearLabel = if (freeClears > 0) {
        stringResource(Res.string.clear_with_count, freeClears)
    } else {
        stringResource(Res.string.clear_with_ad)
    }
    val shakeLabel = if (freeShakes > 0) {
        stringResource(Res.string.shake_with_count, freeShakes)
    } else {
        stringResource(Res.string.shake_with_ad)
    }
    val advertisement = stringResource(Res.string.ad_disclosure)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConsumableIconButton(
            badge = if (freeClears > 0) freeClears.toString() else "AD",
            icon = BombFilled,
            contentDescription = if (isTargeting) stringResource(Res.string.cancel) else {
                if (freeClears == 0) "$clearLabel, $advertisement" else clearLabel
            },
            enabled = isTargeting || hasBodies,
            onClick = if (isTargeting) onCancelClear else onClear,
            modifier = Modifier.semantics { testTag = FruitMergeTestTags.Clear },
        )
        ConsumableIconButton(
            badge = if (freeShakes > 0) freeShakes.toString() else "AD",
            icon = Vibration,
            contentDescription = if (freeShakes == 0) "$shakeLabel, $advertisement" else shakeLabel,
            enabled = !isTargeting && hasBodies,
            onClick = onShake,
            modifier = Modifier.semantics { testTag = FruitMergeTestTags.Shake },
        )
    }
}

@Composable
private fun ConsumableIconButton(
    badge: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BadgedBox(
        modifier = modifier,
        badge = {
            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                Text(badge, fontWeight = FontWeight.Bold)
            }
        },
    ) {
        IconCircleButton(
            icon = icon,
            contentDescription = contentDescription,
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(52.dp),
        )
    }
}

@Composable
private fun SupportingPanel(
    score: Long,
    bestScore: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScoreStrip(score, bestScore, Modifier.fillMaxWidth())
        FruitEvolutionStrip(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .semantics { testTag = FruitMergeTestTags.Evolution },
        )
    }
}

@Composable
private fun ScoreStrip(score: Long, bestScore: Long, modifier: Modifier = Modifier) {
    val scoreDescription = "${stringResource(Res.string.score)} $score"
    val bestDescription = "${stringResource(Res.string.best_score)} $bestScore"
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = scoreDescription },
            )
            Icon(
                imageVector = Crown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = bestScore.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = bestDescription },
            )
        }
    }
}

@Composable
private fun FruitEvolutionStrip(
    modifier: Modifier = Modifier,
) {
    val fruitNames = FruitLevel.entries.map { level -> stringResource(fruitNameResource(level)) }
    val description = fruitNames.joinToString(separator = ", ")
    Surface(
        modifier = modifier.semantics { contentDescription = description },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
            val slotWidth = size.width / FruitLevel.entries.size
            val radius = minOf(slotWidth * 0.40f, size.height * 0.40f)
            FruitLevel.entries.forEachIndexed { index, level ->
                drawFruit(
                    level = level,
                    center = Offset(slotWidth * (index + 0.5f), size.height * 0.53f),
                    radius = radius,
                    angleRadians = 0f,
                    verticalVelocity = 0f,
                    impact = 0f,
                    facePhase = level.ordinal.toFloat(),
                    anxious = false,
                    alpha = 1f,
                )
            }
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

private fun Modifier.fruitMergeDropInput(
    enabled: Boolean,
    onMovePreview: (Float) -> Unit,
    onDrop: (Boolean) -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        val touchSlopSquared = viewConfiguration.touchSlop * viewConfiguration.touchSlop
        onMovePreview((down.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f))
        var last: PointerInputChange = down
        var dragged = false
        while (last.pressed) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            last = change
            if (change.pressed) {
                val offset = change.position - down.position
                if (offset.getDistanceSquared() >= touchSlopSquared) dragged = true
                onMovePreview((change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f))
            }
        }
        onMovePreview((last.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f))
        onDrop(dragged)
    }
}

private const val NANOS_PER_SECOND: Float = 1_000_000_000f
private const val MAX_FRAME_SECONDS: Float = 0.05f
private const val FACE_CLOCK_WRAP_SECONDS: Float = 120f
