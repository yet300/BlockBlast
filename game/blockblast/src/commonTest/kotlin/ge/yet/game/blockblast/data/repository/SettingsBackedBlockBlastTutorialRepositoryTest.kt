package ge.yet.game.blockblast.data.repository

import com.app.common.AppDispatchers
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsBackedBlockBlastTutorialRepositoryTest {

    @Test
    fun markSeen_persists_existing_key_and_updates_state() = runTest {
        val settings = MapSettings()
        val repository = SettingsBackedBlockBlastTutorialRepository(
            settings = settings,
            dispatchers = AppDispatchers(
                default = Dispatchers.Unconfined,
                io = Dispatchers.Unconfined,
            ),
        )

        assertFalse(repository.tutorialSeen.value)

        repository.markSeen()

        assertTrue(repository.tutorialSeen.value)
        assertTrue(settings.getBoolean("blockblast.tutorial_seen", false))
    }

    @Test
    fun existing_seen_value_survives_repository_recreation() {
        val settings = MapSettings("blockblast.tutorial_seen" to true)

        val repository = SettingsBackedBlockBlastTutorialRepository(
            settings = settings,
            dispatchers = AppDispatchers(
                default = Dispatchers.Unconfined,
                io = Dispatchers.Unconfined,
            ),
        )

        assertTrue(repository.tutorialSeen.value)
    }
}
