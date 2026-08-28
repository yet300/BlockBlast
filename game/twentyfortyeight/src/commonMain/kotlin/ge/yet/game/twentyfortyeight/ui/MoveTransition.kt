package ge.yet.game.twentyfortyeight.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import ge.yet.game.twentyfortyeight.engine.Board
import ge.yet.game.twentyfortyeight.engine.MoveResult
import ge.yet.game.twentyfortyeight.engine.Position
import ge.yet.game.twentyfortyeight.engine.RuntimeBoard
import ge.yet.game.twentyfortyeight.engine.TileId
import ge.yet.game.twentyfortyeight.engine.UndoTransition
import ge.yet.game.twentyfortyeight.store.VisualTransition
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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

private data class TransitionTileVisual(
    val positionProgress: Float,
    val alpha: Float,
    val scale: Float,
)

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
    val spatialProgress = remember { Animatable(0f) }

    LaunchedEffect(transition.transitionId, policy) {
        if (highestCompletedId?.let { transition.transitionId <= it } == true) {
            progress.snapTo(1f)
            spatialProgress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        spatialProgress.snapTo(0f)
        if (policy == MotionPolicy.Normal) {
            coroutineScope {
                val spatialJob = launch {
                    spatialProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            stiffness = MotionPolicy.Normal.slideStiffness,
                            dampingRatio = MotionPolicy.Normal.slideDampingRatio,
                        ),
                    )
                }
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = policy.transitionDurationMs,
                        easing = LinearEasing,
                    ),
                )
                spatialJob.cancelAndJoin()
                spatialProgress.snapTo(1f)
            }
        } else {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = policy.transitionDurationMs,
                    easing = LinearEasing,
                ),
            )
            spatialProgress.snapTo(1f)
        }
        highestCompletedId = transition.transitionId
        currentOnCompleted(transition.transitionId)
    }

    TransitionTileLayout(
        tiles = transitionTiles(transition, policy),
        progress = { progress.value },
        spatialProgress = { spatialProgress.value },
        modifier = modifier,
    )
}

@Composable
private fun TransitionTileLayout(
    tiles: List<TransitionTileModel>,
    progress: () -> Float,
    spatialProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            tiles.forEach { tile ->
                key(tile.key) {
                    TwentyFortyEightTile(value = tile.value)
                }
            }
        },
        modifier = modifier,
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
            val frameSpatialProgress = spatialProgress().coerceIn(0f, 1f)
            tiles.forEachIndexed { index, tile ->
                val visual = tileVisual(tile.kind, frameProgress, frameSpatialProgress)
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
                    scaleX = visual.scale
                    scaleY = visual.scale
                }
            }
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
    kind: TransitionTileKind,
    progress: Float,
    spatialProgress: Float,
): TransitionTileVisual = when (kind) {
    TransitionTileKind.Stable -> TransitionTileVisual(spatialProgress, alpha = 1f, scale = 1f)
    TransitionTileKind.MergeSource -> TransitionTileVisual(
        positionProgress = spatialProgress,
        alpha = 1f - segment(progress, 0.42f, 0.55f),
        scale = 1f - 0.22f * segment(progress, 0.30f, 0.55f),
    )
    TransitionTileKind.MergeResult -> {
        val reveal = segment(progress, 0.42f, 0.56f)
        val pulse = if (progress < 0.72f) {
            segment(progress, 0.42f, 0.72f)
        } else {
            1f - segment(progress, 0.72f, 1f)
        }
        TransitionTileVisual(1f, alpha = reveal, scale = 1f + 0.12f * pulse)
    }
    TransitionTileKind.Spawn -> {
        val reveal = segment(progress, 0.52f, 1f)
        TransitionTileVisual(1f, alpha = reveal, scale = 0.72f + 0.28f * reveal)
    }
    TransitionTileKind.FadeOut -> TransitionTileVisual(1f, 1f - progress, 1f)
    TransitionTileKind.FadeIn -> TransitionTileVisual(1f, progress, 1f)
}

private fun segment(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun interpolate(start: Int, end: Int, progress: Float): Int =
    (start + (end - start) * progress).roundToInt()
