package ge.yet.game.feature.home.integration

import ge.yet.game.feature.home.integration.stateToModel
import ge.yet.game.feature.home.store.HomeStore
import kotlin.test.Test
import kotlin.test.assertEquals

class MappersTest {

    @Test
    fun maps_hasSavedGame_through() {
        val model = stateToModel(HomeStore.State(hasSavedGame = true))
        assertEquals(true, model.hasSavedGame)
    }

    @Test
    fun maps_default_state() {
        val model = stateToModel(HomeStore.State())
        assertEquals(false, model.hasSavedGame)
    }
}
