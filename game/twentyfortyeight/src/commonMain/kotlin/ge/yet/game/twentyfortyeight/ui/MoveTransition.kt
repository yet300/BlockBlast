package ge.yet.game.twentyfortyeight.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import ge.yet.game.twentyfortyeight.engine.Board
import ge.yet.game.twentyfortyeight.engine.MoveResult
import ge.yet.game.twentyfortyeight.engine.Position
import ge.yet.game.twentyfortyeight.engine.RuntimeBoard
import ge.yet.game.twentyfortyeight.engine.TileId
import ge.yet.game.twentyfortyeight.engine.UndoTransition
import ge.yet.game.twentyfortyeight.store.VisualTransition
import kotlin.math.roundToInt

private enum class TransitionTileKind {
    Stable,
    MergeSource,
    MergeResult,
    Spawn,
    FadeOut,
    FadeIn,
}

private data class TransitionTileModel(
    val key: String,
    val value: Long,
    val fromIndex: Int,
    val toIndex: Int,
    val kind: TransitionTileKind,
)

private data class MergeEffectModel(
    val sourceIndices: List<Int>,
    val targetIndex: Int,
    val resultValue: Long,
)

internal data class TransitionTileVisual(
    val positionProgress: Float,
    val alpha: Float,
    val scaleX: Float,
    val scaleY: Float,
)

internal data class MergeEffectVisual(
    val bridgeAlpha: Float,
    val bridgeWidthFraction: Float,
    val haloAlpha: Float,
    val haloRadiusFraction: Float,
) {
    companion object {
        val Hidden = MergeEffectVisual(0f, 0f, 0f, 0f)
    }
}

internal fun mergeSourceVisual(
    progress: Float,
    horizontalTravel: Boolean,
): TransitionTileVisual {
    val frame = progress.coerceIn(0f, 1f)
    val stretch = triangle(frame, start = 0.08f, peak = 0.40f, end = 0.58f)
    val impact = segment(frame, 0.44f, 0.60f)
    val along = (1f + 0.10f * stretch - 0.16f * impact).coerceIn(0.84f, 1.10f)
    val across = (1f - 0.06f * stretch + 0.10f * impact).coerceIn(0.84f, 1.10f)
    return TransitionTileVisual(
        positionProgress = spatialCurve(frame),
        alpha = 1f - segment(frame, 0.46f, 0.60f),
        scaleX = if (horizontalTravel) along else across,
        scaleY = if (horizontalTravel) across else along,
    )
}

internal fun mergeResultVisual(progress: Float): TransitionTileVisual {
    val frame = progress.coerceIn(0f, 1f)
    val reveal = segment(frame, 0.44f, 0.60f)
    val scale = if (frame <= 0.72f) {
        interpolate(0.82f, 1.08f, segment(frame, 0.44f, 0.72f))
    } else {
        interpolate(1.08f, 1f, segment(frame, 0.72f, 1f))
    }
    return TransitionTileVisual(1f, reveal, scale, scale)
}

internal fun mergeEffectVisual(
    progress: Float,
    enabled: Boolean,
): MergeEffectVisual {
    if (!enabled) return MergeEffectVisual.Hidden
    val frame = progress.coerceIn(0f, 1f)
    val bridge = triangle(frame, start = 0.10f, peak = 0.38f, end = 0.62f)
    val halo = triangle(frame, start = 0.40f, peak = 0.58f, end = 0.82f)
    return MergeEffectVisual(
        bridgeAlpha = 0.44f * bridge,
        bridgeWidthFraction = if (bridge == 0f) 0f else 0.10f + 0.08f * bridge,
        haloAlpha = 0.35f * halo,
        haloRadiusFraction = if (halo == 0f) {
            0f
        } else {
            0.30f + 0.42f * segment(frame, 0.40f, 0.82f)
        },
    )
}

@Composable
internal fun VisualTransitionView(
    transition: VisualTransition,
    policy: MotionPolicy,
    onCompleted: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnCompleted by rememberUpdatedState(onCompleted)
    var highestCompletedId by remember { mutableStateOf<Long?>(null) }
    val progress = remember { Animatable(0f) }
    val tiles = remember(transition.transitionId, policy) {
        transitionTiles(transition, policy)
    }
    val mergeEffects = remember(transition.transitionId, policy) {
        transitionMergeEffects(transition, policy)
    }

    LaunchedEffect(transition.transitionId, policy) {
        if (highestCompletedId?.let { transition.transitionId <= it } == true) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = policy.transitionDurationMs,
                easing = LinearEasing,
            ),
        )
        highestCompletedId = transition.transitionId
        currentOnCompleted(transition.transitionId)
    }

    TransitionTileLayout(
        tiles = tiles,
        mergeEffects = mergeEffects,
        progress = { progress.value },
        modifier = modifier,
    )
}

@Composable
private fun TransitionTileLayout(
    tiles: List<TransitionTileModel>,
    mergeEffects: List<MergeEffectModel>,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val tileTheme = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        TileTheme.Dark
    } else {
        TileTheme.Light
    }
    val effectColors = remember(mergeEffects, tileTheme) {
        mergeEffects.map { effect -> TileStylePolicy.style(effect.resultValue, tileTheme).background }
    }
    val liquidEffectModifier = if (mergeEffects.isEmpty()) {
        Modifier
    } else {
        Modifier.drawWithCache {
            val side = minOf(size.width, size.height)
            val outerPadding = 8.dp.toPx()
            val spacing = 8.dp.toPx()
            val cellSize = ((side - outerPadding * 2f - spacing * (Board.SIZE - 1)) / Board.SIZE)
                .coerceAtLeast(0f)
            val cellCenters = List(Board.CELL_COUNT) { index ->
                cellCenter(
                    index = index,
                    outerPadding = outerPadding,
                    spacing = spacing,
                    cellSize = cellSize,
                )
            }
            onDrawBehind {
                val frame = progress().coerceIn(0f, 1f)
                val visual = mergeEffectVisual(frame, enabled = true)
                if (visual == MergeEffectVisual.Hidden) return@onDrawBehind
                val positionProgress = spatialCurve(frame)
                mergeEffects.forEachIndexed { effectIndex, effect ->
                    val target = cellCenters[effect.targetIndex]
                    val color = effectColors[effectIndex]
                    effect.sourceIndices.forEach { sourceIndex ->
                        val source = cellCenters[sourceIndex]
                        val current = Offset(
                            x = interpolate(source.x, target.x, positionProgress),
                            y = interpolate(source.y, target.y, positionProgress),
                        )
                        drawLine(
                            color = color.copy(alpha = visual.bridgeAlpha),
                            start = current,
                            end = target,
                            strokeWidth = cellSize * visual.bridgeWidthFraction,
                            cap = StrokeCap.Round,
                        )
                    }
                    drawCircle(
                        color = color.copy(alpha = visual.haloAlpha),
                        radius = cellSize * visual.haloRadiusFraction,
                        center = target,
                    )
                }
            }
        }
    }
    Layout(
        content = {
            tiles.forEach { tile ->
                key(tile.key) {
                    TwentyFortyEightTile(value = tile.value)
                }
            }
        },
        modifier = modifier.then(liquidEffectModifier),
    ) { measurables, constraints ->
        val side = minOf(constraints.maxWidth, constraints.maxHeight)
        val outerPadding = 8.dp.roundToPx()
        val spacing = 8.dp.roundToPx()
        val cellSize = ((side - outerPadding * 2 - spacing * (Board.SIZE - 1)) / Board.SIZE)
            .coerceAtLeast(0)
        val childConstraints = Constraints.fixed(cellSize, cellSize)
        val placeables = measurables.map { it.measure(childConstraints) }

        layout(side, side) {
            val frameProgress = progress().coerceIn(0f, 1f)
            tiles.forEachIndexed { index, tile ->
                val visual = tileVisual(tile, frameProgress)
                val from = Position.fromIndex(tile.fromIndex)
                val to = Position.fromIndex(tile.toIndex)
                val fromX = cellOffset(from.column, outerPadding, spacing, cellSize)
                val fromY = cellOffset(from.row, outerPadding, spacing, cellSize)
                val toX = cellOffset(to.column, outerPadding, spacing, cellSize)
                val toY = cellOffset(to.row, outerPadding, spacing, cellSize)
                val x = interpolate(fromX, toX, visual.positionProgress)
                val y = interpolate(fromY, toY, visual.positionProgress)
                placeables[index].placeWithLayer(x, y) {
                    alpha = visual.alpha
                    scaleX = visual.scaleX
                    scaleY = visual.scaleY
                }
            }
        }
    }
}

private fun transitionMergeEffects(
    transition: VisualTransition,
    policy: MotionPolicy,
): List<MergeEffectModel> {
    if (!policy.usesSpatialMotion || transition !is VisualTransition.Move) return emptyList()
    val motionsById = transition.result.motions.associateBy { motion -> motion.sourceId }
    return transition.result.merges.map { merge ->
        MergeEffectModel(
            sourceIndices = merge.sourceIds.map { sourceId ->
                requireNotNull(motionsById[sourceId]).source.index
            },
            targetIndex = merge.target.index,
            resultValue = merge.resultValue.value,
        )
    }.also { effects ->
        require(effects.size <= Board.CELL_COUNT / 2) {
            "Merge effect count exceeds the board bound: ${effects.size}"
        }
    }
}

private fun transitionTiles(
    transition: VisualTransition,
    policy: MotionPolicy,
): List<TransitionTileModel> {
    if (!policy.usesSpatialMotion) {
        val (before, after) = transition.boards()
        return crossfadeTiles(before, after)
    }
    return when (transition) {
        is VisualTransition.Move -> moveTiles(transition.result)
        is VisualTransition.Undo -> when (val undo = transition.transition) {
            is UndoTransition.Reverse -> reverseUndoTiles(undo)
            is UndoTransition.Crossfade -> crossfadeTiles(undo.beforeBoard, undo.restoredBoard)
        }
    }
}

private fun moveTiles(move: MoveResult.Changed): List<TransitionTileModel> {
    val mergeSourceIds = move.merges.flatMapTo(mutableSetOf()) { it.sourceIds }
    val sourceTiles = move.motions.map { motion ->
        val tile = requireNotNull(move.beforeBoard[motion.source])
        TransitionTileModel(
            key = "move-source-${motion.sourceId.value}",
            value = tile.value.value,
            fromIndex = motion.source.index,
            toIndex = motion.target.index,
            kind = if (motion.sourceId in mergeSourceIds) {
                TransitionTileKind.MergeSource
            } else {
                TransitionTileKind.Stable
            },
        )
    }
    val mergeResults = move.merges.map { merge ->
        TransitionTileModel(
            key = "move-merge-${merge.resultId.value}",
            value = merge.resultValue.value,
            fromIndex = merge.target.index,
            toIndex = merge.target.index,
            kind = TransitionTileKind.MergeResult,
        )
    }
    val spawn = TransitionTileModel(
        key = "move-spawn-${move.spawn.id.value}",
        value = move.spawn.value.value,
        fromIndex = move.spawn.position.index,
        toIndex = move.spawn.position.index,
        kind = TransitionTileKind.Spawn,
    )
    return sourceTiles + mergeResults + spawn
}

private fun reverseUndoTiles(undo: UndoTransition.Reverse): List<TransitionTileModel> {
    val movingSourceIds = undo.motions.mapTo(mutableSetOf()) { it.sourceId }
    val moving = undo.motions.map { motion ->
        val restored = requireNotNull(undo.restoredBoard[motion.target])
        TransitionTileModel(
            key = "undo-${motion.sourceId.value}-${motion.restoredId.value}",
            value = restored.value.value,
            fromIndex = motion.source.index,
            toIndex = motion.target.index,
            kind = TransitionTileKind.Stable,
        )
    }
    val removed = undo.beforeBoard.tiles.mapIndexedNotNull { index, tile ->
        tile?.takeIf { it.id !in movingSourceIds }?.let {
            TransitionTileModel(
                key = "undo-removed-${it.id.value}",
                value = it.value.value,
                fromIndex = index,
                toIndex = index,
                kind = TransitionTileKind.FadeOut,
            )
        }
    }
    return moving + removed
}

private fun crossfadeTiles(
    before: RuntimeBoard,
    after: RuntimeBoard,
): List<TransitionTileModel> =
    boardTiles(before, "before", TransitionTileKind.FadeOut) +
        boardTiles(after, "after", TransitionTileKind.FadeIn)

private fun boardTiles(
    board: RuntimeBoard,
    keyPrefix: String,
    kind: TransitionTileKind,
): List<TransitionTileModel> = board.tiles.mapIndexedNotNull { index, tile ->
    tile?.let {
        TransitionTileModel(
            key = "$keyPrefix-${it.id.value}",
            value = it.value.value,
            fromIndex = index,
            toIndex = index,
            kind = kind,
        )
    }
}

private fun VisualTransition.boards(): Pair<RuntimeBoard, RuntimeBoard> = when (this) {
    is VisualTransition.Move -> result.beforeBoard to result.finalBoard
    is VisualTransition.Undo -> when (val undo = transition) {
        is UndoTransition.Reverse -> undo.beforeBoard to undo.restoredBoard
        is UndoTransition.Crossfade -> undo.beforeBoard to undo.restoredBoard
    }
}

private fun tileVisual(
    tile: TransitionTileModel,
    progress: Float,
): TransitionTileVisual = when (tile.kind) {
    TransitionTileKind.Stable -> TransitionTileVisual(
        spatialCurve(progress),
        alpha = 1f,
        scaleX = 1f,
        scaleY = 1f,
    )
    TransitionTileKind.MergeSource -> mergeSourceVisual(
        progress = progress,
        horizontalTravel = Position.fromIndex(tile.fromIndex).row ==
            Position.fromIndex(tile.toIndex).row,
    )
    TransitionTileKind.MergeResult -> mergeResultVisual(progress)
    TransitionTileKind.Spawn -> {
        val reveal = segment(progress, 0.52f, 1f)
        val scale = 0.72f + 0.28f * reveal
        TransitionTileVisual(1f, reveal, scale, scale)
    }
    TransitionTileKind.FadeOut -> TransitionTileVisual(1f, 1f - progress, 1f, 1f)
    TransitionTileKind.FadeIn -> TransitionTileVisual(1f, progress, 1f, 1f)
}

private fun segment(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun triangle(value: Float, start: Float, peak: Float, end: Float): Float =
    if (value <= peak) segment(value, start, peak) else 1f - segment(value, peak, end)

private fun spatialCurve(progress: Float): Float {
    val remaining = 1f - progress.coerceIn(0f, 1f)
    return 1f - remaining * remaining * remaining
}

private fun interpolate(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress

private fun cellCenter(
    index: Int,
    outerPadding: Float,
    spacing: Float,
    cellSize: Float,
): Offset = Offset(
    x = outerPadding + (index % Board.SIZE) * (cellSize + spacing) + cellSize / 2f,
    y = outerPadding + (index / Board.SIZE) * (cellSize + spacing) + cellSize / 2f,
)

private fun interpolate(start: Int, end: Int, progress: Float): Int =
    (start + (end - start) * progress).roundToInt()
