package ge.yet.game.miniapp.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MiniAppStorageKeyTest {

    @Test
    fun `new storage keys are namespaced by stable mini app id`() {
        assertEquals("miniapp.game.snake.save", MiniAppId("game.snake").storageKey("save").value)
    }

    @Test
    fun `storage helper rejects malformed ids and local key names`() {
        assertFailsWith<IllegalArgumentException> { MiniAppId("Game.Snake").storageKey("save") }
        listOf("", "best-score", "nested.key", "2save").forEach { localName ->
            assertFailsWith<IllegalArgumentException> {
                MiniAppId("game.snake").storageKey(localName)
            }
        }
    }
}
