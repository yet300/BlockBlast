package ge.yet.game.fruitmerge

import ge.yet.game.miniapp.compose.MiniAppFrameMode
import kotlin.test.Test
import kotlin.test.assertEquals

class FruitMergeBackgroundPolicyTest {
    @Test
    fun `content-only result uses the result background behind host chrome`() {
        assertEquals(
            FruitMergeBackgroundRole.RESULT,
            fruitMergeBackgroundRole(MiniAppFrameMode.ContentOnly),
        )
        assertEquals(
            FruitMergeBackgroundRole.PLAYING,
            fruitMergeBackgroundRole(MiniAppFrameMode.Standard),
        )
    }
}
