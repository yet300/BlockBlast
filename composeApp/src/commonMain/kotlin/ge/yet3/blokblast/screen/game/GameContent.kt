package ge.yet3.blokblast.screen.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.best
import blockblast.composeapp.generated.resources.cd_back
import blockblast.composeapp.generated.resources.cd_settings
import blockblast.composeapp.generated.resources.exit_to_home
import blockblast.composeapp.generated.resources.game_over
import blockblast.composeapp.generated.resources.game_over_subtitle
import blockblast.composeapp.generated.resources.new_best
import blockblast.composeapp.generated.resources.restart
import blockblast.composeapp.generated.resources.revive
import blockblast.composeapp.generated.resources.score
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.blockblast.feature.game.GameComponent
import ge.yet3.blokblast.component.background.AmbientMeshBackground
import ge.yet3.blokblast.component.icon.ArrowBack
import ge.yet3.blokblast.component.icon.Settings
import ge.yet3.blokblast.component.overlay.GameOverOverlay
import ge.yet3.blokblast.component.score.ScoreChip
import ge.yet3.blokblast.screen.game.effects.GlitchState
import ge.yet3.blokblast.screen.game.effects.ShakeState
import ge.yet3.blokblast.screen.game.effects.comboStripes
import ge.yet3.blokblast.screen.game.effects.glitchEffect
import ge.yet3.blokblast.screen.game.effects.rememberComboStripesState
import ge.yet3.blokblast.screen.game.effects.rememberGlitchState
import ge.yet3.blokblast.screen.game.effects.rememberShakeState
import ge.yet3.blokblast.screen.game.effects.shake
import ge.yet3.blokblast.theme.pieceColor
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun GameContent(component: GameComponent) {
    val model by component.model.subscribeAsState()
    var selectedPieceId by remember { mutableStateOf<Long?>(null) }

    // ── Effect states ────────────────────────────────────────────────────
    val dragDrop = rememberDragDropState()
    val shakeState = rememberShakeState()
    val glitchState = rememberGlitchState()
    val comboStripes = rememberComboStripesState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Grid measurement (populated by GameGrid's onGloballyPositioned)
    var gridOriginX by remember { mutableFloatStateOf(0f) }
    var gridOriginY by remember { mutableFloatStateOf(0f) }
    var cellSizePx by remember { mutableFloatStateOf(0f) }
    var gapPx by remember { mutableFloatStateOf(0f) }

    // ── Track combo level changes → trigger line-clear effects ───────────
    var prevComboLevel by remember { mutableStateOf(model.comboLevel) }
    LaunchedEffect(model.comboLevel) {
        if (model.comboLevel > prevComboLevel && model.comboLevel > 0) {
            comboStripes.sweep()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        prevComboLevel = model.comboLevel
    }

    // ── Game over → glitch effect ────────────────────────────────────────
    LaunchedEffect(model.isGameOver) {
        if (model.isGameOver) {
            glitchState.trigger()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
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
                .glitchEffect(glitchState),
        ) {
            AmbientMeshBackground(
                modifier = Modifier.fillMaxSize(),
                baseColor = MaterialTheme.colorScheme.background,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(20.dp))

                GameGrid(
                    grid = model.grid,
                    selectedPiece = model.currentPieces.firstOrNull { it.pieceId == selectedPieceId },
                    onCellTapped = { x, y ->
                        val id = selectedPieceId
                        if (id != null) {
                            component.onCellClicked(id, x, y)
                            selectedPieceId = null
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .shake(shakeState),
                    dragDropState = dragDrop,
                    comboStripes = comboStripes,
                    onGridMeasured = { ox, oy, cs, gp ->
                        gridOriginX = ox
                        gridOriginY = oy
                        cellSizePx = cs
                        gapPx = gp
                    },
                )

                Spacer(Modifier.height(24.dp))

                PieceTray(
                    pieces = model.currentPieces,
                    selectedPieceId = selectedPieceId,
                    onPieceSelected = { id ->
                        selectedPieceId = if (selectedPieceId == id) null else id
                    },
                    onDragStart = { piece, startPos, offset ->
                        selectedPieceId = null
                        dragDrop.startDrag(piece, startPos, offset)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragMove = { position ->
                        dragDrop.updateDrag(
                            position = position,
                            gridOrigin = androidx.compose.ui.geometry.Offset(gridOriginX, gridOriginY),
                            cellSizePx = cellSizePx,
                            gapPx = gapPx,
                            grid = model.grid,
                        )
                    },
                    onDragEnd = {
                        val piece = dragDrop.draggedPiece
                        val anchor = dragDrop.hoverAnchor
                        if (piece != null && anchor != null && dragDrop.isValidPlacement) {
                            // Valid drop — place piece
                            component.onCellClicked(piece.pieceId, anchor.first, anchor.second)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } else if (piece != null) {
                            // Invalid drop — shake + haptic reject
                            scope.launch { shakeState.shake() }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        dragDrop.endDrag()
                    },
                )

                Spacer(Modifier.height(24.dp))
            }

            // ── Floating dragged piece overlay ───────────────────────────
            if (dragDrop.isDragging) {
                val piece = dragDrop.draggedPiece!!
                val color = pieceColor(piece.colorId)
                val dragCellSize = 36.dp
                val dragGap = 2.dp

                Box(
                    modifier = Modifier
                        .offset {
                            val pos = dragDrop.dragPosition
                            val fingerOff = dragDrop.fingerOffset
                            IntOffset(
                                x = (pos.x - fingerOff.x - dragCellSize.toPx() * piece.shape.width / 2f).toInt(),
                                y = (pos.y - fingerOff.y - dragCellSize.toPx() * piece.shape.height - 80f).toInt(),
                            )
                        }
                        .graphicsLayer {
                            scaleX = 1.15f
                            scaleY = 1.15f
                            alpha = 0.85f
                            shadowElevation = 16f
                        },
                ) {
                    val totalW = piece.shape.width * dragCellSize + (piece.shape.width - 1) * dragGap
                    val totalH = piece.shape.height * dragCellSize + (piece.shape.height - 1) * dragGap

                    Box(modifier = Modifier.size(totalW, totalH)) {
                        piece.shape.cells.forEach { pos ->
                            BlockPiece(
                                color = color,
                                cellSize = dragCellSize,
                                filled = true,
                                modifier = Modifier.offset(
                                    x = pos.x * (dragCellSize + dragGap),
                                    y = pos.y * (dragCellSize + dragGap),
                                ),
                            )
                        }
                    }
                }
            }

            val isNewBest = model.isGameOver &&
                model.score > 0L &&
                model.score >= model.bestScore

            GameOverOverlay(
                visible = model.isGameOver,
                score = model.score,
                bestScore = model.bestScore,
                isNewBest = isNewBest,
                canRevive = model.revivesUsed < 1,
                onReviveClicked = {
                    selectedPieceId = null
                    component.onReviveClicked()
                },
                onRestartClicked = {
                    selectedPieceId = null
                    component.onRestartClicked()
                },
                onExitClicked = component::onExitClicked,
                title = stringResource(Res.string.game_over),
                subtitle = stringResource(Res.string.game_over_subtitle),
                scoreLabel = stringResource(Res.string.score),
                bestLabel = stringResource(Res.string.best),
                newBestLabel = stringResource(Res.string.new_best),
                reviveLabel = stringResource(Res.string.revive),
                restartLabel = stringResource(Res.string.restart),
                exitLabel = stringResource(Res.string.exit_to_home),
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScoreChip(label = scoreLabel, value = score)
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

@Composable
private fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
        )
    }
}
