package ge.yet.blockblast.feature.settings.main.integration

import ge.yet.blockblast.feature.settings.main.store.SettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals

class MappersTest {

    @Test
    fun maps_all_flags_through() {
        val model = stateToModel(
            SettingsStore.State(music = false, sfx = true, vibration = true, dark = true),
        )
        assertEquals(false, model.musicEnabled)
        assertEquals(true, model.sfxEnabled)
        assertEquals(true, model.vibrationEnabled)
        assertEquals(true, model.darkTheme)
    }

    @Test
    fun maps_default_state() {
        val model = stateToModel(SettingsStore.State())
        assertEquals(true, model.musicEnabled)
        assertEquals(true, model.sfxEnabled)
        assertEquals(true, model.vibrationEnabled)
        assertEquals(false, model.darkTheme)
    }
}
