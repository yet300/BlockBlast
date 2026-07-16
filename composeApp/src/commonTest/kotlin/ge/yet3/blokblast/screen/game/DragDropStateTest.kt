package ge.yet3.blokblast.screen.game

import androidx.compose.ui.geometry.Offset
import ge.yet.blokblast.domain.model.Piece
import ge.yet.blokblast.domain.model.Polyomino
import ge.yet.blokblast.domain.model.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DragDropStateTest {

    @Test
    fun invalid_drop_transitions_from_dragging_to_returning_to_idle() {
        val state = DragDropState()
        val piece = piece()
        val sourceCenter = Offset(72f, 640f)

        state.startDrag(
            piece = piece,
            startPosition = Offset(80f, 620f),
            pieceOriginOffset = Offset(12f, 18f),
            sourcePosition = sourceCenter,
        )

        assertEquals(DragPresentation.Dragging, state.presentation)
        assertEquals(sourceCenter, state.sourcePosition)
        assertTrue(state.isDragging)

        state.beginReturn()

        assertEquals(DragPresentation.Returning, state.presentation)
        assertEquals(piece, state.draggedPiece)
        assertEquals(sourceCenter, state.sourcePosition)
        assertFalse(state.isDragging)
        assertTrue(state.isReturning)

        state.finishReturn()

        assertEquals(DragPresentation.Idle, state.presentation)
        assertNull(state.draggedPiece)
        assertEquals(Offset.Zero, state.sourcePosition)
        assertFalse(state.isReturning)
    }

    @Test
    fun return_snapshots_the_last_drag_position() {
        val state = DragDropState()
        val start = Offset(80f, 620f)
        val source = Offset(72f, 640f)

        state.startDrag(
            piece = piece(),
            startPosition = start,
            pieceOriginOffset = Offset.Zero,
            sourcePosition = source,
        )
        state.beginReturn()

        assertEquals(start, state.returnStartPosition)
        assertEquals(source, state.sourcePosition)
    }

    @Test
    fun valid_drop_ends_immediately_without_returning() {
        val state = DragDropState()

        state.startDrag(
            piece = piece(),
            startPosition = Offset(80f, 620f),
            pieceOriginOffset = Offset.Zero,
            sourcePosition = Offset(72f, 640f),
        )
        state.endDrag()

        assertEquals(DragPresentation.Idle, state.presentation)
        assertNull(state.draggedPiece)
        assertFalse(state.isReturning)
    }

    private fun piece() = Piece(
        pieceId = 1L,
        shape = Polyomino(
            id = "single",
            cells = listOf(Position(0, 0)),
        ),
        colorId = 0,
    )
}
