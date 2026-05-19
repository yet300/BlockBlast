package ge.yet.blockblast.feature.game.tray

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import ge.yet.blokblast.domain.model.GameState
import ge.yet.blokblast.domain.model.Grid
import ge.yet.blokblast.domain.model.Piece
import ge.yet.blokblast.domain.model.Polyomino
import ge.yet.blokblast.domain.model.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class DefaultPieceTrayComponentTest {

    private val dot = Polyomino(id = "dot", cells = listOf(Position(0, 0)))
    private fun piece(id: Long) = Piece(pieceId = id, shape = dot, colorId = 0)

    private fun build(initial: List<Piece>): Pair<DefaultPieceTrayComponent, MutableValue<GameState>> {
        val state = MutableValue(GameState(grid = Grid(), currentPieces = initial))
        val lifecycle = LifecycleRegistry().apply { resume() }
        val ctx = DefaultComponentContext(lifecycle)
        val component = DefaultPieceTrayComponent(ctx, state)
        return component to state
    }

    @Test
    fun fresh_tray_emits_pieces_in_engine_order() {
        val (component, _) = build(listOf(piece(1), piece(2), piece(3)))
        val slots = component.slots.value
        assertEquals(listOf(1L, 2L, 3L), slots.map { it.piece.pieceId })
        assertEquals(listOf(0, 1, 2), slots.map { it.spawnIndex })
    }

    @Test
    fun placing_middle_piece_keeps_survivor_component_instances() {
        val (component, state) = build(listOf(piece(1), piece(2), piece(3)))
        val originalA = component.slots.value[0]
        val originalC = component.slots.value[2]

        // Engine compacts: [1, 2, 3] → place 2 → [1, 3]
        state.value = state.value.copy(currentPieces = listOf(piece(1), piece(3)))

        val slots = component.slots.value
        assertEquals(2, slots.size)
        assertSame(originalA, slots[0])
        assertSame(originalC, slots[1]) // C shifted from index 2 → 1, instance preserved
    }

    @Test
    fun full_refill_creates_new_slot_instances() {
        val (component, state) = build(listOf(piece(1), piece(2), piece(3)))
        val before = component.slots.value.toList()

        state.value = state.value.copy(currentPieces = emptyList())
        assertEquals(emptyList(), component.slots.value)

        state.value = state.value.copy(currentPieces = listOf(piece(10), piece(20), piece(30)))
        val after = component.slots.value
        assertEquals(listOf(10L, 20L, 30L), after.map { it.piece.pieceId })
        before.zip(after).forEach { (b, a) -> assertNotSame(b, a) }
    }

    @Test
    fun tap_toggles_selection_and_clearSelection_resets() {
        val (component, _) = build(listOf(piece(1), piece(2), piece(3)))
        val slot1 = component.slots.value[1]
        slot1.onTap()
        assertEquals(2L, component.selection.value.piece?.pieceId)
        assertEquals(true, slot1.isSelected.value)

        slot1.onTap()
        assertNull(component.selection.value.piece)

        slot1.onTap()
        component.clearSelection()
        assertNull(component.selection.value.piece)
    }

    @Test
    fun placing_selected_piece_clears_selection() {
        val (component, state) = build(listOf(piece(1), piece(2), piece(3)))
        component.slots.value[1].onTap()
        assertEquals(2L, component.selection.value.piece?.pieceId)

        state.value = state.value.copy(currentPieces = listOf(piece(1), piece(3)))
        assertNull(component.selection.value.piece)
    }
}
