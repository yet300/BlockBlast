package ge.yet.blockblast.feature.home.integration

import ge.yet.blockblast.feature.home.store.HomeStore
import kotlin.test.Test
import kotlin.test.assertEquals

class MappersTest {

    @Test
    fun maps_bestScore_and_hasSavedGame_through() {
        val model = stateToModel(HomeStore.State(bestScore = 1234L, hasSavedGame = true))
        assertEquals(1234L, model.bestScore)
        assertEquals(true, model.hasSavedGame)
    }

    @Test
    fun maps_default_state() {
        val model = stateToModel(HomeStore.State())
        assertEquals(0L, model.bestScore)
        assertEquals(false, model.hasSavedGame)
    }
}
