package ge.yet.game.fruitmerge

import ge.yet.game.miniapp.compose.MiniAppFrameMode
import kotlin.test.Test
import kotlin.test.assertEquals

class FruitMergeBackgroundPolicyTest {
    @Test
    fun `playing and game over share one background behind host chrome`() {
        assertEquals(
            FruitMergeBackgroundRole.MARKET,
            fruitMergeBackgroundRole(MiniAppFrameMode.ContentOnly),
        )
        assertEquals(
            FruitMergeBackgroundRole.MARKET,
            fruitMergeBackgroundRole(MiniAppFrameMode.Standard),
        )
    }
}
