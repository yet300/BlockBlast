package ge.yet.game.miniapp.testkit

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MutableMiniAppStorageTest {
    @Test
    fun remove_emits_the_observers_declared_default() = runTest {
        val storage = MutableMiniAppStorage()
        val observed = storage.observeLong("score", defaultValue = 42L)
        storage.putLong("score", 99L)

        storage.remove("score")

        assertEquals(42L, observed.first())
        assertEquals(42L, storage.getLong("score", 42L))
    }

    @Test
    fun primitive_types_and_local_names_are_isolated() = runTest {
        val storage = MutableMiniAppStorage()

        storage.putBoolean("tutorial", true)
        storage.putString("save", "payload")

        assertEquals(true, storage.getBoolean("tutorial"))
        assertEquals("payload", storage.getString("save"))
        assertEquals("missing", storage.getString("other", "missing"))
    }
}
