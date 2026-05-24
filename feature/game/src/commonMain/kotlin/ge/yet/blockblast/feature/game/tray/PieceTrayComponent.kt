package ge.yet.blockblast.feature.game.tray

import com.arkivanov.decompose.value.Value
import ge.yet.blokblast.domain.model.Piece

/**
 * Bottom-of-screen tray that holds up to three pieces. Mirrors the engine's
 * compacted `currentPieces` list while keeping the per-piece component
 * instance stable across emissions (keyed by [Piece.pieceId]), so the UI can
 * animate position changes without resetting per-slot animation state.
 */
interface PieceTrayComponent {
    /**
     * Compacted list of 0..3 slots — same order and length as the engine's
     * `currentPieces`. Slot identity is keyed by `pieceId`, so when a piece is
     * placed its [TraySlotComponent] is dropped from the list while survivors
     * retain their existing instances at their new (shifted) indices.
     */
    val slots: Value<List<TraySlotComponent>>

    /**
     * Wrapped because [Value] forbids nullable type arguments — see
     * [TraySelection.piece] for the contained piece, if any.
     */
    val selection: Value<TraySelection>

    fun clearSelection()
}

/** Non-null wrapper around an optional tray selection. */
data class TraySelection(val piece: Piece? = null) {
    companion object {
        val NONE = TraySelection()
    }
}
