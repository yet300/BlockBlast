package ge.yet.game.twentyfortyeight.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ScoreBestRowTest {
    @Test
    fun `zero best presents only the current score`() {
        assertEquals(
            ScoreCardState.ScoreOnly,
            scoreCardState(bestScore = 0L, bestImprovedInRun = false),
        )
    }

    @Test
    fun `persisted best presents score crown and best`() {
        assertEquals(
            ScoreCardState.ScoreAndBest,
            scoreCardState(bestScore = 4096L, bestImprovedInRun = false),
        )
    }

    @Test
    fun `improved run remains best only regardless of later score`() {
        assertEquals(
            ScoreCardState.BestOnly,
            scoreCardState(bestScore = 256L, bestImprovedInRun = true),
        )
    }
}
