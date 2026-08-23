package ge.yet.game.blockblast.data.repository

import ge.yet.game.miniapp.testkit.MutableMiniAppStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsBackedBlockBlastTutorialRepositoryTest {

    @Test
    fun markSeen_persists_existing_key_and_updates_state() = runTest {
        val storage = MutableMiniAppStorage()
        val repository = SettingsBackedBlockBlastTutorialRepository(
            storage = BlockBlastStorage(storage),
            scope = backgroundScope,
        )

        assertFalse(repository.tutorialSeen.value)

        repository.markSeen()
        runCurrent()

        assertTrue(repository.tutorialSeen.value)
        assertTrue(storage.getBoolean("tutorial_seen", false))
    }

    @Test
    fun existing_seen_value_survives_repository_recreation() = runTest {
        val storage = MutableMiniAppStorage(mapOf("tutorial_seen" to true))

        val repository = SettingsBackedBlockBlastTutorialRepository(
            storage = BlockBlastStorage(storage),
            scope = backgroundScope,
        )
        runCurrent()

        assertTrue(repository.tutorialSeen.value)
    }
}
