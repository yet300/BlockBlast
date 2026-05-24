package ge.yet.blockblast.feature.game.tray

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import ge.yet.blokblast.domain.model.Piece

internal class DefaultTraySlotComponent(
    override val piece: Piece,
    override val spawnIndex: Int,
    selection: Value<TraySelection>,
    private val onToggleSelection: (Long) -> Unit,
) : TraySlotComponent {

    private val canFitState = MutableValue(true)
    override val canFit: Value<Boolean> = canFitState

    override val isSelected: Value<Boolean> =
        selection.map { it.piece?.pieceId == piece.pieceId }

    override fun onTap() {
        onToggleSelection(piece.pieceId)
    }

    fun updateCanFit(value: Boolean) {
        if (canFitState.value != value) canFitState.value = value
    }
}
