package ge.yet.blockblast.feature.settings.integration

import ge.yet.blockblast.feature.settings.store.SettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals

class MappersTest {

    @Test
    fun maps_three_flags_through() {
        val model = stateToModel(
            SettingsStore.State(sound = false, vibration = true, dark = true),
        )
        assertEquals(false, model.soundEnabled)
        assertEquals(true, model.vibrationEnabled)
        assertEquals(true, model.darkTheme)
    }

    @Test
    fun maps_default_state() {
        val model = stateToModel(SettingsStore.State())
        assertEquals(true, model.soundEnabled)
        assertEquals(true, model.vibrationEnabled)
        assertEquals(false, model.darkTheme)
    }
}
