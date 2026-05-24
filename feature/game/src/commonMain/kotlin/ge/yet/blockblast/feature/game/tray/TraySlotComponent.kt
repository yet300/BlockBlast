package ge.yet.blockblast.feature.game.tray

import com.arkivanov.decompose.value.Value
import ge.yet.blokblast.domain.model.Piece

/**
 * One piece in the tray. Identity (`==`) is stable across engine emissions as
 * long as the same [Piece.pieceId] is still in play — placing the piece drops
 * the component, while merely reordering the tray (e.g. neighbours placed)
 * keeps this instance alive so its UI-side animation state survives.
 */
interface TraySlotComponent {
    val piece: Piece

    /**
     * Index this slot held at the moment it was created — used by the
     * entrance animation to pick a fly-in direction (left/right/bottom). Stays
     * fixed for the lifetime of the slot; the *current* index can shift if
     * neighbours are placed.
     */
    val spawnIndex: Int

    val isSelected: Value<Boolean>

    /** Whether [piece] can fit anywhere on the current grid. */
    val canFit: Value<Boolean>

    /** Toggle selection of this slot's piece. */
    fun onTap()
}
