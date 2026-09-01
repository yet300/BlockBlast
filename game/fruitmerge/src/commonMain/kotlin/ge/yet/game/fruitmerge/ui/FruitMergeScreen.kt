package ge.yet.game.fruitmerge.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.engine.RunPhase
import ge.yet.game.fruitmerge.engine.TargetingMode
import ge.yet.game.fruitmerge.generated.resources.Res
import ge.yet.game.fruitmerge.generated.resources.ad_disclosure
import ge.yet.game.fruitmerge.generated.resources.apple
import ge.yet.game.fruitmerge.generated.resources.blueberry
import ge.yet.game.fruitmerge.generated.resources.board_description
import ge.yet.game.fruitmerge.generated.resources.cancel
import ge.yet.game.fruitmerge.generated.resources.clear_with_ad
import ge.yet.game.fruitmerge.generated.resources.clear_with_count
import ge.yet.game.fruitmerge.generated.resources.raspberry
import ge.yet.game.fruitmerge.generated.resources.danger_line
import ge.yet.game.fruitmerge.generated.resources.loading_game
import ge.yet.game.fruitmerge.generated.resources.mandarin
import ge.yet.game.fruitmerge.generated.resources.watermelon
import ge.yet.game.fruitmerge.generated.resources.next_fruit
import ge.yet.game.fruitmerge.generated.resources.peach
import ge.yet.game.fruitmerge.generated.resources.pear
import ge.yet.game.fruitmerge.generated.resources.pineapple
import ge.yet.game.fruitmerge.generated.resources.lime
import ge.yet.game.fruitmerge.generated.resources.shake_with_ad
import ge.yet.game.fruitmerge.generated.resources.shake_with_count
import ge.yet.game.fruitmerge.generated.resources.strawberry
import ge.yet.game.fruitmerge.session.FruitMergeComponent
import ge.yet.game.fruitmerge.session.PaidActionToken
import ge.yet.game.uikit.adaptive.AdaptiveGameScaffold
import ge.yet.game.uikit.components.icon.BombFilled
import ge.yet.game.uikit.components.icon.Vibration
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal object FruitMergeTestTags {
    const val Background = "fruit_merge_market_background"
    const val Viewport = "fruit_merge_viewport"
    const val Board = "fruit_merge_board"
    const val Support = "fruit_merge_support"
    const val Clear = "fruit_merge_clear"
    const val Shake = "fruit_merge_shake"
    const val Evolution = "fruit_merge_evolution"
    const val Score = "fruit_merge_score"
    const val Next = "fruit_merge_next"
    const val Tutorial = "fruit_merge_tutorial"
    const val TutorialSkip = "fruit_merge_tutorial_skip"
    const val Result = "fruit_merge_result"
    const val ResultScore = "fruit_merge_result_score"
    const val ResultBest = "fruit_merge_result_best"
    const val ResultLargestFruit = "fruit_merge_result_largest_fruit"
    const val NewGame = "fruit_merge_new_game"
    const val MarketCrate = Board
    const val NextBasket = Next
    const val FruitSlicer = Clear
    const val CrateHandle = Shake
    const val PriceTag = Score
    const val EvolutionShelf = Evolution
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
    var presentationTimeSeconds by remember(component) { mutableFloatStateOf(0f) }
    var viewportOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var boardBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var nextFruitAnchor by remember { mutableStateOf<FruitAnchor?>(null) }
    var transfer by remember(component) { mutableStateOf<FruitTransfer?>(null) }
    var transferSequence by remember(component) { mutableIntStateOf(0) }
    var presentationSequence by remember(component) { mutableIntStateOf(0) }
    val activePresentations = remember(component) { mutableStateListOf<ActiveFruitPresentation>() }
    val latestPresentationTime = rememberUpdatedState(presentationTimeSeconds)

    LaunchedEffect(component) {
        component.presentationEvents.collect { event ->
            presentationSequence += 1
            activePresentations += ActiveFruitPresentation(
                id = presentationSequence.toLong(),
                event = event,
                startedAtSeconds = latestPresentationTime.value,
            )
        }
    }

    LaunchedEffect(component, model.initialized, model.visible, model.game.phase, reducedMotion) {
        if (!model.initialized || !model.visible || model.game.phase != RunPhase.PLAYING) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val elapsed = ((now - previous) / NANOS_PER_SECOND).coerceIn(0f, MAX_FRAME_SECONDS)
            previous = now
            component.frame(elapsed)
            val nextPresentationTime = presentationTimeSeconds + elapsed
            presentationTimeSeconds = nextPresentationTime
            if (activePresentations.isNotEmpty()) {
                activePresentations.removeAll { it.isExpired(nextPresentationTime) }
            }
            if (!reducedMotion) faceTimeSeconds = (faceTimeSeconds + elapsed) % FACE_CLOCK_WRAP_SECONDS
        }
    }

    val game = model.game
    val dropEnabled = model.initialized && model.tutorialReady &&
        game.phase == RunPhase.PLAYING && game.targetingMode == TargetingMode.NONE &&
        game.dropCooldownSeconds <= 0f && transfer == null
    val latestDropHandler = rememberUpdatedState<(Boolean) -> Unit> { dragged ->
        val anchor = nextFruitAnchor
        if (!reducedMotion && anchor != null && boardBoundsInRoot.width > 0f && boardBoundsInRoot.height > 0f) {
            transferSequence += 1
            transfer = FruitTransfer(
                id = transferSequence,
                level = game.nextPreviewLevel,
                sourceCenterInRoot = anchor.centerInRoot,
                sourceRadius = anchor.radius,
                targetCenterInRoot = fruitPreviewCenterInRoot(boardBoundsInRoot, game.previewX),
                targetRadius = game.nextPreviewLevel.radius * minOf(boardBoundsInRoot.width, boardBoundsInRoot.height),
            )
        }
        component.drop(dragged)
    }
    val onDrop = remember(component) {
        { dragged: Boolean -> latestDropHandler.value(dragged) }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                viewportOriginInRoot = coordinates.positionInRoot()
            }
            .fruitMergeDropInput(
                enabled = dropEnabled,
                onMovePreview = component::movePreview,
                onDrop = onDrop,
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
            verticalPrimaryWeight = 5f,
            header = {
                GameHeader(
                    nextLevel = game.nextPreviewLevel,
                    faceTimeSeconds = faceTimeSeconds,
                    reducedMotion = reducedMotion,
                    freeClears = game.freeClears,
                    freeShakes = game.freeShakes,
                    hasBodies = game.bodies.isNotEmpty(),
                    isTargeting = game.targetingMode == TargetingMode.CLEAR,
                    isShaking = game.shakeStepsRemaining > 0,
                    shakeStepsRemaining = game.shakeStepsRemaining,
                    onClear = {
                        val token = component.requestClearGate()
                        if (token != null) requestClearAd(token)
                    },
                    onCancelClear = component::cancelClear,
                    onShake = {
                        val token = component.requestShakeGate()
                        if (token != null) requestShakeAd(token)
                    },
                    onNextFruitAnchorChanged = { anchor ->
                        if (anchor != nextFruitAnchor) nextFruitAnchor = anchor
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                )
            },
            primary = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    FruitMergeBoard(
                        game = game,
                        faceTimeSeconds = faceTimeSeconds,
                        reducedMotion = reducedMotion,
                        boardDescription = boardDescription,
                        dangerDescription = dangerDescription,
                        onClearTarget = component::selectClearTarget,
                        showPreview = transfer == null || reducedMotion,
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                boardBoundsInRoot = coordinates.boundsInRoot()
                            }
                            .semantics { testTag = FruitMergeTestTags.Board },
                    )
                }
            },
            supporting = {
                SupportingPanel(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                )
            },
        )
        val boardBoundsInViewport = Rect(
            left = boardBoundsInRoot.left - viewportOriginInRoot.x,
            top = boardBoundsInRoot.top - viewportOriginInRoot.y,
            right = boardBoundsInRoot.right - viewportOriginInRoot.x,
            bottom = boardBoundsInRoot.bottom - viewportOriginInRoot.y,
        )
        val activeTransfer = transfer
        if (activeTransfer != null) {
            FruitTransferOverlay(
                transfer = activeTransfer,
                viewportOriginInRoot = viewportOriginInRoot,
                faceTimeSeconds = faceTimeSeconds,
                onFinished = { finishedId ->
                    if (transfer?.id == finishedId) transfer = null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        FruitMergePresentation(
            events = activePresentations,
            nowSeconds = presentationTimeSeconds,
            boardBoundsInViewport = boardBoundsInViewport,
            modifier = Modifier.fillMaxSize(),
        )
        if (model.screen is FruitMergeComponent.ScreenState.Playing) {
            FruitMergeTutorial(
                step = model.tutorialStep,
                boardBoundsInViewport = boardBoundsInViewport,
                previewX = game.previewX,
                reducedMotion = reducedMotion,
                onSkip = component::skipTutorial,
                onComplete = component::completeTutorial,
                modifier = Modifier.fillMaxSize(),
            )
        }
        (model.screen as? FruitMergeComponent.ScreenState.GameOver)?.let { screen ->
            FruitMergeGameOverOverlay(
                screen = screen,
                faceTimeSeconds = faceTimeSeconds,
                reducedMotion = reducedMotion,
                onNewGame = component::newGame,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun GameHeader(
    nextLevel: FruitLevel,
    faceTimeSeconds: Float,
    reducedMotion: Boolean,
    freeClears: Int,
    freeShakes: Int,
    hasBodies: Boolean,
    isTargeting: Boolean,
    isShaking: Boolean,
    shakeStepsRemaining: Int,
    onClear: () -> Unit,
    onCancelClear: () -> Unit,
    onShake: () -> Unit,
    onNextFruitAnchorChanged: (FruitAnchor) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionHud(
                freeClears = freeClears,
                freeShakes = freeShakes,
                hasBodies = hasBodies,
                isTargeting = isTargeting,
                isShaking = isShaking,
                handleRotationDegrees = crateHandleRotation(shakeStepsRemaining, reducedMotion),
                onClear = onClear,
                onCancelClear = onCancelClear,
                onShake = onShake,
            )
            Spacer(Modifier.weight(1f))
            NextFruitCard(
                level = nextLevel,
                faceTimeSeconds = faceTimeSeconds,
                reducedMotion = reducedMotion,
                onFruitAnchorChanged = onNextFruitAnchorChanged,
            )
        }
    }
}

@Composable
private fun NextFruitCard(
    level: FruitLevel,
    faceTimeSeconds: Float,
    reducedMotion: Boolean,
    onFruitAnchorChanged: (FruitAnchor) -> Unit,
) {
    val palette = rememberFruitMergePalette()
    val label = stringResource(Res.string.next_fruit)
    val fruit = stringResource(fruitNameResource(level))
    val accentColor = palette.coral
    Surface(
        modifier = Modifier
            .width(136.dp)
            .height(56.dp)
            .semantics {
                testTag = FruitMergeTestTags.Next
                contentDescription = "$label: $fruit"
        },
        shape = MaterialTheme.shapes.large,
        color = palette.paper,
        contentColor = palette.ink,
        border = BorderStroke(2.dp, palette.woodDark.copy(alpha = 0.62f)),
        shadowElevation = 3.dp,
    ) {
        Box {
            Canvas(Modifier.fillMaxSize()) {
                repeat(6) { index ->
                    val y = size.height * (0.18f + index * 0.13f)
                    drawLine(
                        color = palette.woodDark.copy(alpha = 0.10f),
                        start = Offset(size.width * 0.05f, y),
                        end = Offset(size.width * 0.95f, y + size.height * 0.04f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
            FruitPreview(
                level = level,
                faceTimeSeconds = faceTimeSeconds,
                reducedMotion = reducedMotion,
                modifier = Modifier
                    .size(46.dp)
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot()
                        onFruitAnchorChanged(
                            FruitAnchor(
                                centerInRoot = bounds.center,
                                radius = minOf(bounds.width, bounds.height) * FRUIT_PREVIEW_RADIUS_FRACTION,
                            ),
                        )
                    },
            )
            Canvas(Modifier.size(width = 14.dp, height = 22.dp)) {
                val stroke = 2.5.dp.toPx()
                drawLine(
                    color = accentColor,
                    start = Offset(size.width * 0.72f, size.height * 0.22f),
                    end = Offset(size.width * 0.30f, size.height * 0.50f),
                    strokeWidth = stroke,
                )
                drawLine(
                    color = accentColor,
                    start = Offset(size.width * 0.30f, size.height * 0.50f),
                    end = Offset(size.width * 0.72f, size.height * 0.78f),
                    strokeWidth = stroke,
                )
            }
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.ink,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FruitTransferOverlay(
    transfer: FruitTransfer,
    viewportOriginInRoot: Offset,
    faceTimeSeconds: Float,
    onFinished: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val latestOnFinished = rememberUpdatedState(onFinished)
    LaunchedEffect(transfer.id) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = FRUIT_TRANSFER_DURATION_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        )
        latestOnFinished.value(transfer.id)
    }
    Canvas(modifier) {
        val t = progress.value
        val oneMinusT = 1f - t
        val source = transfer.sourceCenterInRoot - viewportOriginInRoot
        val target = transfer.targetCenterInRoot - viewportOriginInRoot
        val control = Offset(
            x = (source.x + target.x) * 0.5f,
            y = minOf(source.y, target.y) - 54.dp.toPx(),
        )
        val center = source * (oneMinusT * oneMinusT) +
            control * (2f * oneMinusT * t) +
            target * (t * t)
        val radius = transfer.sourceRadius + (transfer.targetRadius - transfer.sourceRadius) * t
        drawFruit(
            level = transfer.level,
            center = center,
            radius = radius,
            angleRadians = 0f,
            verticalVelocity = 0f,
            impact = 0f,
            facePhase = faceTimeSeconds + transfer.level.ordinal,
            danger = DangerVisual(0f, false),
            alpha = 1f,
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
    isShaking: Boolean,
    handleRotationDegrees: Float,
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
        MarketToolButton(
            badge = if (freeClears > 0) freeClears.toString() else "AD",
            icon = BombFilled,
            contentDescription = if (isTargeting) stringResource(Res.string.cancel) else {
                if (freeClears == 0) "$clearLabel, $advertisement" else clearLabel
            },
            enabled = isTargeting || hasBodies,
            onClick = if (isTargeting) onCancelClear else onClear,
            modifier = Modifier.semantics { testTag = FruitMergeTestTags.Clear },
        )
        MarketToolButton(
            badge = if (freeShakes > 0) freeShakes.toString() else "AD",
            icon = Vibration,
            contentDescription = if (freeShakes == 0) "$shakeLabel, $advertisement" else shakeLabel,
            enabled = !isTargeting && hasBodies && !isShaking,
            onClick = onShake,
            iconRotationDegrees = handleRotationDegrees,
            modifier = Modifier.semantics { testTag = FruitMergeTestTags.Shake },
        )
    }
}

@Composable
private fun SupportingPanel(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FruitEvolutionStrip(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .semantics { testTag = FruitMergeTestTags.Evolution },
        )
    }
}

@Composable
private fun FruitEvolutionStrip(
    modifier: Modifier = Modifier,
) {
    val palette = rememberFruitMergePalette()
    val fruitNames = FruitLevel.entries.map { level -> stringResource(fruitNameResource(level)) }
    val description = fruitNames.joinToString(separator = ", ")
    Surface(
        modifier = modifier.semantics { contentDescription = description },
        shape = MaterialTheme.shapes.extraLarge,
        color = palette.woodLight,
        border = BorderStroke(2.dp, palette.woodDark.copy(alpha = 0.62f)),
        shadowElevation = 2.dp,
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
                    danger = DangerVisual(0f, false),
                    alpha = 1f,
                )
            }
        }
    }
}

internal fun fruitNameResource(level: FruitLevel): StringResource = when (level) {
    FruitLevel.BLUEBERRY -> Res.string.blueberry
    FruitLevel.RASPBERRY -> Res.string.raspberry
    FruitLevel.STRAWBERRY -> Res.string.strawberry
    FruitLevel.LIME -> Res.string.lime
    FruitLevel.MANDARIN -> Res.string.mandarin
    FruitLevel.APPLE -> Res.string.apple
    FruitLevel.PEAR -> Res.string.pear
    FruitLevel.PEACH -> Res.string.peach
    FruitLevel.PINEAPPLE -> Res.string.pineapple
    FruitLevel.WATERMELON -> Res.string.watermelon
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

private data class FruitAnchor(
    val centerInRoot: Offset,
    val radius: Float,
)

private data class FruitTransfer(
    val id: Int,
    val level: FruitLevel,
    val sourceCenterInRoot: Offset,
    val sourceRadius: Float,
    val targetCenterInRoot: Offset,
    val targetRadius: Float,
)

private const val NANOS_PER_SECOND: Float = 1_000_000_000f
private const val MAX_FRAME_SECONDS: Float = 0.05f
private const val FACE_CLOCK_WRAP_SECONDS: Float = 120f
private const val FRUIT_PREVIEW_RADIUS_FRACTION: Float = 0.34f
private const val FRUIT_TRANSFER_DURATION_MILLIS: Int = 340
