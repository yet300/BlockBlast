package ge.yet3.blokblast.screen.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import ge.yet3.blokblast.component.overlay.GestureTutorial
import ge.yet3.blokblast.theme.LocalOnTutorialSeen
import ge.yet3.blokblast.theme.LocalTutorialSeen
import ge.yet3.blokblast.theme.LocalVibrationEnabled
import ge.yet.blokblast.domain.model.Grid
import androidx.compose.ui.unit.dp
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.best
import blockblast.composeapp.generated.resources.cd_back
import blockblast.composeapp.generated.resources.cd_settings
import blockblast.composeapp.generated.resources.score
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.blockblast.feature.game.GameComponent
import ge.yet3.blokblast.ads.AdBanner
import ge.yet3.blokblast.component.background.AmbientMeshBackground
import ge.yet3.blokblast.component.button.IconCircleButton
import ge.yet3.blokblast.component.icon.ArrowBack
import ge.yet3.blokblast.component.icon.Settings
import ge.yet3.blokblast.component.score.ScoreChip
import ge.yet3.blokblast.screen.game.effects.FeedbackPopupOverlay
import ge.yet3.blokblast.screen.game.effects.FeedbackPopupState
import ge.yet3.blokblast.screen.game.effects.FloatingScoreOverlay
import ge.yet3.blokblast.screen.game.effects.FloatingScoreState
import ge.yet3.blokblast.screen.game.effects.glitchEffect
import ge.yet3.blokblast.screen.game.effects.comboFlash
import ge.yet3.blokblast.screen.game.effects.comboZoom
import ge.yet3.blokblast.screen.game.effects.rememberComboPunchState
import ge.yet3.blokblast.screen.game.effects.rememberComboStripesState
import ge.yet3.blokblast.screen.game.effects.rememberParticleBurstState
import ge.yet3.blokblast.screen.game.effects.rememberGlitchState
import ge.yet3.blokblast.theme.pieceColor
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

// Ghost-piece visual constants. Shared with DragDropState so the snap
// target always matches where the floating ghost is rendered.
//
// All three are Dp — the drag pipeline converts to pixels on demand using
// LocalDensity, and the cached pixel values in `cellSizePx`/`gapPx` come
// from GameGrid.onGloballyPositioned, which re-fires on every relayout
// (rotation, foldable unfold, multi-window split). So density changes are
// transparent. The only edge case — density flipping *mid-drag* — doesn't
// happen on real devices and is intentionally not handled.
private val DRAG_GHOST_CELL_SIZE = 36.dp
private val DRAG_GHOST_GAP = 2.dp
private val DRAG_GHOST_VERTICAL_LIFT = 28.dp

@Composable
fun BlockBlastGameContent(component: GameComponent) {
    val uiModel by component.model.subscribeAsState()
    val model = uiModel.game
    val traySelection by component.pieceTray.selection.subscribeAsState()
    val selectedPiece = traySelection.piece
    val traySlots by component.pieceTray.slots.subscribeAsState()

    // ── Effect states ────────────────────────────────────────────────────
    val dragDrop = rememberDragDropState()
    val glitchState = rememberGlitchState()
    val comboStripes = rememberComboStripesState()
    val particleBurst = rememberParticleBurstState()
    val comboPunch = rememberComboPunchState()
    val floatingScore = remember { FloatingScoreState() }
    val feedbackPopups = remember { FeedbackPopupState() }
    val haptic = LocalHapticFeedback.current
    val vibrationEnabled = LocalVibrationEnabled.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reducedMotion = rememberReducedMotion()
    val clearMotionGate = remember { OneShotMotionGate<Int>() }
    val gameOverMotionGate = remember { OneShotMotionGate<Unit>() }
    val screenMotion = gameMotionPolicy(
        comboLevel = model.comboLevel,
        hasDragHoverTarget = false,
        hasPrediction = false,
        reducedMotion = reducedMotion,
    )

    // Grid measurement (populated by GameGrid's onGloballyPositioned)
    var gridOriginX by remember { mutableFloatStateOf(0f) }
    var gridOriginY by remember { mutableFloatStateOf(0f) }
    var cellSizePx by remember { mutableFloatStateOf(0f) }
    var gapPx by remember { mutableFloatStateOf(0f) }

    // Bounds (root-coords) used by the first-launch gesture tutorial.
    var gridBounds by remember { mutableStateOf(Rect.Zero) }
    var trayBounds by remember { mutableStateOf(Rect.Zero) }
    val tutorialSeen = LocalTutorialSeen.current
    val onTutorialSeen = LocalOnTutorialSeen.current

    // The wordless tutorial dismisses itself the moment the player engages —
    // either by dragging a piece or tapping one to select it. Dismissal is
    // local + immediate (a fade-out + confetti) so it never lags behind the
    // async "seen" persistence; the flag is persisted once the exit finishes.
    var tutorialDismissing by remember { mutableStateOf(false) }
    var tutorialDismissed by remember { mutableStateOf(false) }
    val userEngaged = dragDrop.isDragging || selectedPiece != null
    LaunchedEffect(userEngaged) {
        if (userEngaged && !tutorialSeen) tutorialDismissing = true
    }

    var prevComboLevel by remember { mutableStateOf(model.comboLevel) }
    LaunchedEffect(model.comboLevel, reducedMotion) {
        if (model.comboLevel > prevComboLevel && model.comboLevel > 0) {
            // First pulse — always fires
            haptic.vibrateIf(vibrationEnabled, HapticFeedbackType.LongPress)
            // Second pulse for combo ≥ 3 — double-tap feel
            if (model.comboLevel >= 3) {
                kotlinx.coroutines.delay(90)
                haptic.vibrateIf(vibrationEnabled, HapticFeedbackType.LongPress)
            }
            // Third pulse for combo ≥ 6 — triple-tap feel
            if (model.comboLevel >= 6) {
                kotlinx.coroutines.delay(90)
                haptic.vibrateIf(vibrationEnabled, HapticFeedbackType.LongPress)
            }
            if (model.comboLevel >= 2) {
                feedbackPopups.add(type = null, comboLevel = model.comboLevel)
                // Localize the flash on the centroid of the cells that were
                // just cleared, so the radial bloom appears to emanate from
                // the actual point of impact instead of washing the screen.
                val cleared = model.lastClearedCells.cells
                val origin = if (cleared.isNotEmpty() && cellSizePx > 0f) {
                    val avgX = cleared.map { it.x }.average().toFloat()
                    val avgY = cleared.map { it.y }.average().toFloat()
                    val step = cellSizePx + gapPx
                    androidx.compose.ui.geometry.Offset(
                        x = gridOriginX + avgX * step + cellSizePx / 2f,
                        y = gridOriginY + avgY * step + cellSizePx / 2f,
                    )
                } else null
                if (!reducedMotion) {
                    scope.launch { comboPunch.punch(model.comboLevel, origin) }
                }
            }
        }
        prevComboLevel = model.comboLevel
    }

    // ── Directional haptic: tick each time drag crosses a grid cell ───────
    LaunchedEffect(dragDrop.hoverAnchor) {
        if (dragDrop.isDragging && dragDrop.hoverAnchor != null) {
            haptic.vibrateIf(vibrationEnabled, HapticFeedbackType.TextHandleMove)
        }
    }

    LaunchedEffect(model.lastFeedback) {
        if (model.lastFeedback.type != null) {
            feedbackPopups.add(type = model.lastFeedback.type, comboLevel = null)
        }
    }

    LaunchedEffect(model.lastPointsAwarded) {
        val points = model.lastPointsAwarded.points
        if (points > 0) {
            val cx = gridOriginX + (Grid.SIZE * cellSizePx + (Grid.SIZE - 1) * gapPx) / 2f
            val cy = gridOriginY + (Grid.SIZE * cellSizePx + (Grid.SIZE - 1) * gapPx) / 2f
            floatingScore.add(points, androidx.compose.ui.geometry.Offset(cx, cy))
        }
    }

    LaunchedEffect(model.lastClearedCells.nonce, reducedMotion) {
        val clearEvent = model.lastClearedCells
        val motionDecision = clearMotionGate.consume(
            eventIdentity = clearEvent.nonce,
            motionEnabled = !reducedMotion,
        )
        if (!motionDecision.shouldRunMotion) return@LaunchedEffect

        val cells = clearEvent.cells
        if (cells.isNotEmpty()) {
            val rows = cells.groupBy { it.y }.filterValues { it.size == 8 }.keys.toList()
            val cols = cells.groupBy { it.x }.filterValues { it.size == 8 }.keys.toList()
            if (rows.isNotEmpty() || cols.isNotEmpty()) {
                launch { comboStripes.sweep(rows, cols) }

                // Cascade ordering: each cleared line gets its own slot in a
                // sequence (rows first, then cols, in their natural order),
                // and every cell inherits the slot of the *first* line it
                // belongs to. Multi-line clears now ripple instead of all
                // popping at the same instant.
                val lineKeys: List<Pair<Char, Int>> =
                    rows.map { 'r' to it } + cols.map { 'c' to it }
                val cellLineSlot = HashMap<Pair<Int, Int>, Int>(cells.size)
                lineKeys.forEachIndexed { idx, (kind, value) ->
                    cells.forEach { pos ->
                        val matches = if (kind == 'r') pos.y == value else pos.x == value
                        val key = pos.x to pos.y
                        if (matches && key !in cellLineSlot) cellLineSlot[key] = idx
                    }
                }
                val perLineDelay = 90L
                // Particle count grows with combo, so chains feel meatier.
                val particleCount = (5 + model.comboLevel.coerceAtMost(5))

                cells.forEach { pos ->
                    val slot = cellLineSlot[pos.x to pos.y] ?: 0
                    val c = ge.yet3.blokblast.theme.pieceColor(
                        ((pos.x * 7 + pos.y * 13) and 0x7FFFFFFF) % 6,
                    )
                    launch {
                        kotlinx.coroutines.delay(slot * perLineDelay)
                        particleBurst.burst(pos.x, pos.y, c, count = particleCount)
                    }
                }
                // Shockwave at every row×column intersection — fire on the
                // later of the two lines so it caps the cascade.
                for (r in rows) for (c in cols) {
                    val rSlot = lineKeys.indexOf('r' to r)
                    val cSlot = lineKeys.indexOf('c' to c)
                    val slot = maxOf(rSlot, cSlot).coerceAtLeast(0)
                    launch {
                        kotlinx.coroutines.delay(slot * perLineDelay)
                        particleBurst.shockwave(c, r, Color.White)
                    }
                }
            }
        }
    }

    // ── Game over → glitch effect ────────────────────────────────────────
    LaunchedEffect(model.isGameOver, reducedMotion) {
        if (!model.isGameOver) {
            gameOverMotionGate.reset()
            return@LaunchedEffect
        }

        val motionDecision = gameOverMotionGate.consume(
            eventIdentity = Unit,
            motionEnabled = !reducedMotion,
        )
        if (!motionDecision.isNewEvent) return@LaunchedEffect

        if (motionDecision.shouldRunMotion) glitchState.trigger()
        haptic.vibrateIf(vibrationEnabled, HapticFeedbackType.LongPress)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            GameTopBar(
                score = model.score,
                bestScore = model.bestScore,
                scoreLabel = stringResource(Res.string.score),
                bestLabel = stringResource(Res.string.best),
                onExitClicked = component::onExitClicked,
                onSettingsClicked = component::onSettingsClicked,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glitchEffect(glitchState)
                .comboFlash(comboPunch),
        ) {
            AmbientMeshBackground(
                modifier = Modifier.fillMaxSize(),
                baseColor = MaterialTheme.colorScheme.background,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .comboZoom(comboPunch)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 58.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(20.dp))

                GameGrid(
                    grid = model.grid,
                    selectedPiece = selectedPiece,
                    onCellTapped = { x, y ->
                        val piece = selectedPiece
                        if (piece != null) {
                            component.onCellClicked(piece.pieceId, x, y)
                            component.pieceTray.clearSelection()
                        }
                    },
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 500.dp)
                        .onGloballyPositioned { gridBounds = it.boundsInRoot() },
                    dragDropState = dragDrop,
                    comboStripes = comboStripes,
                    particleBurst = particleBurst,
                    comboLevel = model.comboLevel,
                    reducedMotion = reducedMotion,
                    clearedEvent = model.lastClearedCells,
                    isGameOver = model.isGameOver,
                    onGridMeasured = { ox, oy, cs, gp ->
                        gridOriginX = ox
                        gridOriginY = oy
                        cellSizePx = cs
                        gapPx = gp
                    },
                )

                Spacer(Modifier.height(24.dp))

                PieceTray(
                    tray = component.pieceTray,
                    dragEnabled = !dragDrop.isReturning,
                    spatialMotionEnabled = screenMotion.spatialMotionEnabled,
                    modifier = Modifier
                        .widthIn(max = 500.dp)
                        .padding(bottom = 8.dp)
                        .onGloballyPositioned { trayBounds = it.boundsInRoot() },
                    onDragStart = { piece, startPos, offset, sourcePosition ->
                        if (!dragDrop.isDragging && !dragDrop.isReturning) {
                            dragDrop.startDrag(piece, startPos, offset, sourcePosition)
                            haptic.vibrateIf(vibrationEnabled, HapticFeedbackType.LongPress)
                        }
                    },
                    onDragMove = { position ->
                        dragDrop.updateDrag(
                            position = position,
                            gridOrigin = androidx.compose.ui.geometry.Offset(gridOriginX, gridOriginY),
                            cellSizePx = cellSizePx,
                            gapPx = gapPx,
                            grid = model.grid,
                            ghostCellSizePx = with(density) { DRAG_GHOST_CELL_SIZE.toPx() },
                            ghostGapPx = with(density) { DRAG_GHOST_GAP.toPx() },
                            verticalLiftPx = with(density) { DRAG_GHOST_VERTICAL_LIFT.toPx() },
                        )
                    },
                    onDragEnd = {
                        val piece = dragDrop.draggedPiece
                        val anchor = dragDrop.hoverAnchor
                        if (piece != null && anchor != null && dragDrop.isValidPlacement) {
                            // Valid drop — place piece
                            component.onCellClicked(piece.pieceId, anchor.first, anchor.second)
                            haptic.vibrateIf(vibrationEnabled, HapticFeedbackType.TextHandleMove)
                            dragDrop.endDrag()
                        } else if (piece != null) {
                            // Invalid drop — retain the overlay while it returns
                            // to the source slot; the haptic supplies the reject cue.
                            haptic.vibrateIf(vibrationEnabled, HapticFeedbackType.LongPress)
                            dragDrop.beginReturn()
                        } else {
                            dragDrop.endDrag()
                        }
                    },
                )
            }

            // Bottom banner ad — pinned to the actual bottom of the screen
            // (above system bars thanks to innerPadding). Hidden while the
            // user is dragging, the Settings sheet is open, or the Game Over
            // overlay is visible.
            val sheetSlot by component.sheetSlot.subscribeAsState()
            val hideBanner = sheetSlot.child != null || model.isGameOver
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(innerPadding)
                    .padding(bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (!hideBanner) {
                    AdBanner(modifier = Modifier.fillMaxWidth())
                }
            }

            // ── Floating dragged piece overlay ───────────────────────────
            if (dragDrop.isDragging || dragDrop.isReturning) {
                val piece = dragDrop.draggedPiece!!
                DraggedPieceOverlay(
                    piece = piece,
                    color = pieceColor(piece.colorId),
                    cellSize = DRAG_GHOST_CELL_SIZE,
                    gap = DRAG_GHOST_GAP,
                    verticalLift = DRAG_GHOST_VERTICAL_LIFT,
                    dragDropState = dragDrop,
                    reducedMotion = reducedMotion,
                    onReturnFinished = dragDrop::finishReturn,
                )
            }

            // ── First-launch gesture tutorial ───────────────────────────────
            // A wordless looping hand demonstrates the drag gesture. Persisted
            // via Settings so it never appears again, and only renders once both
            // targets have been measured so the spotlight lands on real geometry.
            if (!reducedMotion && !tutorialSeen && !tutorialDismissed &&
                trayBounds != Rect.Zero && gridBounds != Rect.Zero && !model.isGameOver
            ) {
                GestureTutorial(
                    trayBounds = trayBounds,
                    gridBounds = gridBounds,
                    piece = traySlots.firstOrNull()?.piece,
                    captionTopPadding = innerPadding.calculateTopPadding() + 8.dp,
                    dismissing = tutorialDismissing,
                    onExitComplete = {
                        tutorialDismissed = true
                        onTutorialSeen()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // ── Floating score & feedback overlays ──────────────────────────
            FloatingScoreOverlay(
                state = floatingScore,
                reducedMotion = reducedMotion,
                modifier = Modifier.fillMaxSize()
            )
            FeedbackPopupOverlay(
                state = feedbackPopups,
                reducedMotion = reducedMotion,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = 200.dp)
            )

            GameSheet(component = component)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameTopBar(
    score: Long,
    bestScore: Long,
    scoreLabel: String,
    bestLabel: String,
    onExitClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
) {
    CenterAlignedTopAppBar(
        navigationIcon = {
            IconCircleButton(
                icon = ArrowBack,
                contentDescription = stringResource(Res.string.cd_back),
                onClick = onExitClicked,
            )
        },
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer { rotationZ = 45f }
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                    ScoreChip(label = scoreLabel, value = score)
                }
                ScoreChip(label = bestLabel, value = bestScore, highlight = true)
            }
        },
        actions = {
            IconCircleButton(
                icon = Settings,
                contentDescription = stringResource(Res.string.cd_settings),
                onClick = onSettingsClicked,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
    )
}

/** Fires haptic feedback only when [enabled] is true. */
private fun HapticFeedback.vibrateIf(enabled: Boolean, type: HapticFeedbackType) {
    if (enabled) performHapticFeedback(type)
}
