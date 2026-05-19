package ge.yet.blockblast.feature.game.tray

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import ge.yet.blokblast.domain.model.GameState
import ge.yet.blokblast.domain.model.Grid
import ge.yet.blokblast.domain.model.Piece
import ge.yet.blokblast.domain.model.Polyomino

/**
 * Reconciles engine emissions of `currentPieces` (already compacted) onto a
 * variable-length list of slot components. Identity is keyed by [Piece.pieceId]
 * — when the engine re-emits with a piece still alive, the same
 * [DefaultTraySlotComponent] instance is reused, preserving its per-slot UI
 * animation state across reorderings.
 */
internal class DefaultPieceTrayComponent(
    componentContext: ComponentContext,
    state: Value<GameState>,
) : PieceTrayComponent, ComponentContext by componentContext {

    private val slotsState = MutableValue<List<TraySlotComponent>>(emptyList())
    override val slots: Value<List<TraySlotComponent>> = slotsState

    private val selectionState = MutableValue(TraySelection.NONE)
    override val selection: Value<TraySelection> = selectionState

    init {
        val cancellation = state.subscribe { reconcile(it.currentPieces, it.grid) }
        lifecycle.doOnDestroy { cancellation.cancel() }
    }

    override fun clearSelection() {
        if (selectionState.value != TraySelection.NONE) selectionState.value = TraySelection.NONE
    }

    private fun toggleSelection(pieceId: Long) {
        val currentlySelected = selectionState.value.piece?.pieceId
        selectionState.value = when (currentlySelected) {
            pieceId -> TraySelection.NONE
            else -> {
                val piece = slotsState.value.firstOrNull { it.piece.pieceId == pieceId }?.piece
                if (piece != null) TraySelection(piece) else TraySelection.NONE
            }
        }
    }

    private fun reconcile(currentPieces: List<Piece>, grid: Grid) {
        // Re-use existing slot components keyed by pieceId. Survivors keep
        // their instance (and UI animation state); placed pieces drop out.
        @Suppress("UNCHECKED_CAST")
        val existing: Map<Long, DefaultTraySlotComponent> =
            (slotsState.value as List<DefaultTraySlotComponent>)
                .associateBy { it.piece.pieceId }

        val nextSlots = currentPieces.mapIndexed { index, piece ->
            existing[piece.pieceId] ?: newSlot(piece, spawnIndex = index)
        }

        // Refresh canFit on every survivor — the grid can have changed under
        // a stationary piece (line clear) since the last emission.
        nextSlots.forEach { it.updateCanFit(canPlaceAnywhere(it.piece.shape, grid)) }

        // Drop a now-invalid selection (selected piece was placed, or a full
        // refill swapped it out from under the user).
        val livePieceIds = currentPieces.mapTo(HashSet(currentPieces.size)) { it.pieceId }
        val selectedPieceId = selectionState.value.piece?.pieceId
        if (selectedPieceId != null && selectedPieceId !in livePieceIds) {
            selectionState.value = TraySelection.NONE
        }

        slotsState.value = nextSlots
    }

    private fun newSlot(piece: Piece, spawnIndex: Int): DefaultTraySlotComponent =
        DefaultTraySlotComponent(
            piece = piece,
            spawnIndex = spawnIndex,
            selection = selectionState,
            onToggleSelection = ::toggleSelection,
        )
}

/** True if [shape] has at least one valid placement anywhere on [grid]. */
private fun canPlaceAnywhere(shape: Polyomino, grid: Grid): Boolean {
    for (y in 0 until Grid.SIZE) {
        for (x in 0 until Grid.SIZE) {
            if (canPlaceAt(shape, x, y, grid)) return true
        }
    }
    return false
}

private fun canPlaceAt(shape: Polyomino, x: Int, y: Int, grid: Grid): Boolean {
    for (cell in shape.cells) {
        val gx = x + cell.x
        val gy = y + cell.y
        if (!grid.inBounds(gx, gy) || !grid.isEmpty(gx, gy)) return false
    }
    return true
}
