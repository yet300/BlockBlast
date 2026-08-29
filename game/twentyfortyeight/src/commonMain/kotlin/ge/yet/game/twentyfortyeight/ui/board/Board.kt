package ge.yet.game.twentyfortyeight.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import ge.yet.game.twentyfortyeight.engine.Board
import ge.yet.game.twentyfortyeight.engine.Direction
import ge.yet.game.twentyfortyeight.engine.RuntimeBoard
import ge.yet.game.twentyfortyeight.engine.TileId
import ge.yet.game.twentyfortyeight.engine.TileValue
import ge.yet.game.twentyfortyeight.generated.resources.Res
import ge.yet.game.twentyfortyeight.generated.resources.board_empty_cell
import ge.yet.game.twentyfortyeight.generated.resources.board_row_summary
import ge.yet.game.twentyfortyeight.generated.resources.board_summary
import ge.yet.game.twentyfortyeight.generated.resources.move_down
import ge.yet.game.twentyfortyeight.generated.resources.move_left
import ge.yet.game.twentyfortyeight.generated.resources.move_right
import ge.yet.game.twentyfortyeight.generated.resources.move_up
import ge.yet.game.twentyfortyeight.store.VisualTransition
import ge.yet.game.twentyfortyeight.ui.motion.rememberMotionPolicy
import org.jetbrains.compose.resources.stringResource

internal data class BoardTileModel(
    val id: TileId,
    val value: TileValue,
    val cellIndex: Int,
)

internal data class BoardModel(
    val tiles: List<BoardTileModel?>,
    val transition: VisualTransition? = null,
) {
    init {
        require(tiles.size == Board.CELL_COUNT) {
            "Board model must contain exactly ${Board.CELL_COUNT} cells: ${tiles.size}"
        }
        tiles.forEachIndexed { index, tile ->
            require(tile == null || tile.cellIndex == index) {
                "Board tile ${tile?.id} must retain its authoritative cell index"
            }
        }
    }

    constructor(
        board: RuntimeBoard,
        transition: VisualTransition? = null,
    ) : this(
        tiles = board.tiles.mapIndexed { index, tile ->
            tile?.let {
                BoardTileModel(
                    id = it.id,
                    value = it.value,
                    cellIndex = index,
                )
            }
        },
        transition = transition,
    )
}

@Composable
internal fun TwentyFortyEightBoard(
    model: BoardModel,
    onDirection: (Direction) -> Unit,
    modifier: Modifier = Modifier,
    onTileTextLayout: ((Long, TextLayoutResult) -> Unit)? = null,
    onTransitionCompleted: (Long) -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    val emptyCell = stringResource(Res.string.board_empty_cell)
    val summaryCells = model.tiles.map { tile -> tile?.value?.value?.toString() ?: emptyCell }
    val firstRow = stringResource(
        Res.string.board_row_summary,
        1,
        summaryCells[0],
        summaryCells[1],
        summaryCells[2],
        summaryCells[3],
    )
    val secondRow = stringResource(
        Res.string.board_row_summary,
        2,
        summaryCells[4],
        summaryCells[5],
        summaryCells[6],
        summaryCells[7],
    )
    val thirdRow = stringResource(
        Res.string.board_row_summary,
        3,
        summaryCells[8],
        summaryCells[9],
        summaryCells[10],
        summaryCells[11],
    )
    val fourthRow = stringResource(
        Res.string.board_row_summary,
        4,
        summaryCells[12],
        summaryCells[13],
        summaryCells[14],
        summaryCells[15],
    )
    val summary = stringResource(
        Res.string.board_summary,
        firstRow,
        secondRow,
        thirdRow,
        fourthRow,
    )
    val actionLabels = mapOf(
        Direction.Up to stringResource(Res.string.move_up),
        Direction.Down to stringResource(Res.string.move_down),
        Direction.Left to stringResource(Res.string.move_left),
        Direction.Right to stringResource(Res.string.move_right),
    )
    val occupiedTiles = if (model.transition == null) model.tiles.filterNotNull() else emptyList()
    val tileTheme = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        TileTheme.Dark
    } else {
        TileTheme.Light
    }
    val wellColor = boardWellColor(tileTheme)

    val focusModifier = if (focusRequester == null) {
        Modifier
    } else {
        Modifier.focusRequester(focusRequester)
    }
    Layout(
        content = {
            repeat(Board.CELL_COUNT) {
                BoardWell(color = wellColor)
            }
            occupiedTiles.forEach { tile ->
                key(tile.id) {
                    TwentyFortyEightTile(
                        value = tile.value.value,
                        onTextLayout = onTileTextLayout?.let { observer ->
                            { result -> observer(tile.value.value, result) }
                        },
                    )
                }
            }
            model.transition?.let { transition ->
                VisualTransitionView(
                    transition = transition,
                    policy = rememberMotionPolicy(),
                    onCompleted = onTransitionCompleted,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(focusModifier)
            .focusable()
            .semantics(mergeDescendants = true) {
                traversalIndex = 2f
                contentDescription = summary
                customActions = Direction.entries.map { direction ->
                    CustomAccessibilityAction(label = actionLabels.getValue(direction)) {
                        onDirection(direction)
                        true
                    }
                }
            },
    ) { measurables, constraints ->
        val proposedSide = when {
            constraints.hasBoundedWidth && constraints.hasBoundedHeight ->
                minOf(constraints.maxWidth, constraints.maxHeight)
            constraints.hasBoundedWidth -> constraints.maxWidth
            constraints.hasBoundedHeight -> constraints.maxHeight
            else -> maxOf(constraints.minWidth, constraints.minHeight)
        }
        val side = minOf(
            proposedSide.coerceIn(constraints.minWidth, constraints.maxWidth),
            proposedSide.coerceIn(constraints.minHeight, constraints.maxHeight),
        )
        val outerPadding = 8.dp.roundToPx()
        val spacing = 8.dp.roundToPx()
        val cellSize = ((side - outerPadding * 2 - spacing * (Board.SIZE - 1)) / Board.SIZE)
            .coerceAtLeast(0)
        val childConstraints = Constraints.fixed(cellSize, cellSize)
        val transitionIndex = if (model.transition != null) measurables.lastIndex else -1
        val placeables = measurables.mapIndexed { index, measurable ->
            measurable.measure(
                if (index == transitionIndex) Constraints.fixed(side, side) else childConstraints,
            )
        }

        layout(side, side) {
            placeables.take(Board.CELL_COUNT).forEachIndexed { index, placeable ->
                placeable.place(
                    x = cellOffset(index % Board.SIZE, outerPadding, spacing, cellSize),
                    y = cellOffset(index / Board.SIZE, outerPadding, spacing, cellSize),
                )
            }
            occupiedTiles.forEachIndexed { tileIndex, tile ->
                placeables[Board.CELL_COUNT + tileIndex].place(
                    x = cellOffset(tile.cellIndex % Board.SIZE, outerPadding, spacing, cellSize),
                    y = cellOffset(tile.cellIndex / Board.SIZE, outerPadding, spacing, cellSize),
                )
            }
            if (model.transition != null) {
                placeables.last().place(0, 0)
            }
        }
    }
}

@Composable
private fun BoardWell(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color),
    )
}

internal fun boardWellColor(theme: TileTheme): Color = when (theme) {
    TileTheme.Light -> Color(0xFFD8D0C1)
    TileTheme.Dark -> Color(0xFF292826)
}

internal fun cellOffset(index: Int, padding: Int, spacing: Int, cellSize: Int): Int =
    padding + index * (cellSize + spacing)
