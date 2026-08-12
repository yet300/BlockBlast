package ge.yet.game.domain.engine

import ge.yet.game.domain.engine.GameSessionReducer
import ge.yet.game.domain.engine.GameTransition
import ge.yet.game.domain.engine.PlacementRejection
import ge.yet.game.domain.model.GameEvent
import ge.yet.game.domain.model.GameState
import ge.yet.game.domain.model.Grid
import ge.yet.game.domain.model.Piece
import ge.yet.game.domain.model.Polyomino
import ge.yet.game.domain.engine.ScoreCalculator
import ge.yet.game.domain.model.Position
import ge.yet.game.domain.engine.ShapeGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameSessionReducerTest {

    private val single = Polyomino("single", listOf(Position(0, 0)))
    private val generator = FixedShapeGenerator(single)
    private val reducer = GameSessionReducer(generator, ScoreCalculator())

    @Test
    fun seed_best_score_updates_round_baseline_only_when_persisted_value_is_higher() {
        val initial = GameState(bestScore = 50, bestAtRoundStart = 40)

        val raised = reducer.seedBestScore(initial, persistedBestScore = 100)
        val unchanged = reducer.seedBestScore(raised, persistedBestScore = 80)

        assertEquals(100, raised.bestScore)
        assertEquals(100, raised.bestAtRoundStart)
        assertEquals(raised, unchanged)
    }

    @Test
    fun place_returns_new_state_and_matching_fact_without_mutating_input() {
        val initial = GameState(
            currentPieces = listOf(Piece(pieceId = 7, shape = single, colorId = 2)),
            nextPieceId = 7,
        )

        val result = assertIs<GameTransition.Applied>(
            reducer.place(initial, pieceId = 7, x = 2, y = 3),
        )

        assertTrue(initial.grid.isEmpty(2, 3))
        assertFalse(result.state.grid.isEmpty(2, 3))
        assertEquals(1L, result.state.score)
        assertEquals(3, result.state.currentPieces.size)
        assertEquals(listOf(8L, 9L, 10L), result.state.currentPieces.map(Piece::pieceId))
        assertEquals(10L, result.state.nextPieceId)
        assertIs<GameEvent.MoveResolved>(result.fact)
    }

    @Test
    fun rejected_place_returns_reason_and_original_state_remains_unchanged() {
        val initial = GameState(
            grid = Grid().withCell(0, 0, 4),
            currentPieces = listOf(Piece(pieceId = 1, shape = single, colorId = 2)),
            nextPieceId = 1,
        )

        val result = assertIs<GameTransition.Rejected>(
            reducer.place(initial, pieceId = 1, x = 0, y = 0),
        )

        assertEquals(PlacementRejection.OccupiedOrOutOfBounds, result.reason)
        assertEquals(4, initial.grid.colorAt(0, 0))
    }

    @Test
    fun start_new_game_carries_generator_cursor_in_returned_state() {
        val result = reducer.startNewGame(
            seed = 41,
            bestScore = 99,
            allowStarterLayout = false,
        )

        assertEquals(99, result.state.bestScore)
        assertEquals(99, result.state.bestAtRoundStart)
        assertEquals(listOf(1L, 2L, 3L), result.state.currentPieces.map(Piece::pieceId))
        assertEquals(3L, result.state.nextPieceId)
        assertEquals(42L, result.state.nextRandomSeed)
    }

    @Test
    fun revive_returns_playable_state_and_does_not_touch_grid_or_score() {
        val grid = Grid().withCell(4, 5, 3)
        val terminal = GameState(
            grid = grid,
            score = 123,
            currentPieces = listOf(Piece(pieceId = 9, shape = single, colorId = 1)),
            isGameOver = true,
            nextPieceId = 9,
        )

        val result = assertIs<GameTransition.Applied>(reducer.revive(terminal))

        assertEquals(grid, result.state.grid)
        assertEquals(123, result.state.score)
        assertFalse(result.state.isGameOver)
        assertEquals(1, result.state.revivesUsed)
        assertEquals(listOf(10L, 11L, 12L), result.state.currentPieces.map(Piece::pieceId))
        assertEquals(GameEvent.GameStarted, result.fact)
    }

    private class FixedShapeGenerator(
        private val shape: Polyomino,
    ) : ShapeGenerator {
        override fun nextTray(seed: Long?): List<Polyomino> = List(3) { shape }

        override fun smallReviveTray(): List<Polyomino> = List(3) { shape }
    }
}
