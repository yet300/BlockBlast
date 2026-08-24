package ge.yet.game.miniapp.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MiniAppIdTest {

    @Test
    fun `valid ids are namespaced lowercase identifiers`() {
        listOf("game.blockblast", "tool.counter2", "mini.app3.demo").forEach {
            assertTrue(MiniAppId(it).isValid(), it)
        }
    }

    @Test
    fun `invalid ids are rejected by explicit validation`() {
        listOf("blockblast", "Game.BlockBlast", "game.block-blast", ".game", "game.", "game..x")
            .forEach { id ->
                assertFailsWith<IllegalArgumentException> { MiniAppId(id).requireValid() }
            }
    }

    @Test
    fun `construction stays side effect free for registry diagnostics`() {
        assertEquals("Game.BlockBlast", MiniAppId("Game.BlockBlast").value)
    }
}
