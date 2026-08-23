package ge.yet.game.blockblast.data.repository

import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.miniapp.testkit.MutableMiniAppStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MiniAppStorageBackedRepositoriesTest {
    @Test
    fun external_storage_clear_invalidates_warm_save_score_and_tutorial_state() = runTest {
        val storage = MutableMiniAppStorage()
        val scope = backgroundScope
        val blockBlastStorage = BlockBlastStorage(storage)
        val saves = SettingsBackedGameSaveRepository(blockBlastStorage)
        val scores = SettingsBackedBestScoreRepository(blockBlastStorage, scope)
        val tutorial = SettingsBackedBlockBlastTutorialRepository(blockBlastStorage, scope)
        val state = GameState(score = 123L)

        saves.save(state)
        scores.setBestScore(456L)
        tutorial.markSeen()
        runCurrent()
        assertEquals(state, saves.load())
        assertEquals(456L, scores.bestScore.value)
        assertEquals(true, tutorial.tutorialSeen.value)

        storage.remove("game_save")
        storage.remove("best_score")
        storage.remove("tutorial_seen")
        runCurrent()

        assertNull(saves.load())
        assertEquals(0L, scores.bestScore.value)
        assertEquals(false, tutorial.tutorialSeen.value)
    }

}
