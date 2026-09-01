package ge.yet.game.uikit.components.score

import kotlin.test.Test
import kotlin.test.assertEquals

class CompactScoreTest {
    @Test
    fun `score uses a compact suffix and at most one decimal`() {
        assertEquals("999", compactScore(999))
        assertEquals("1K", compactScore(1_000))
        assertEquals("1.2K", compactScore(1_250))
        assertEquals("1M", compactScore(1_000_000))
        assertEquals("1.2B", compactScore(1_250_000_000))
    }

    @Test
    fun `negative values are clamped because game scores cannot be negative`() {
        assertEquals("0", compactScore(-1))
    }
}
