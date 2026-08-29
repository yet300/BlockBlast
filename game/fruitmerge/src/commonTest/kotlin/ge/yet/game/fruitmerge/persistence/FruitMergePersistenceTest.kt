package ge.yet.game.fruitmerge.persistence

import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.engine.FruitMergeState
import ge.yet.game.miniapp.testkit.MutableMiniAppStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FruitMergePersistenceTest {
    @Test
    fun `empty storage restores a fresh run`() = runTest {
        val persistence = FruitMergePersistence(MutableMiniAppStorage())

        assertEquals(FruitMergeState(), persistence.restore())
    }

    @Test
    fun `checkpoint restores run and preserves the highest score`() = runTest {
        val storage = MutableMiniAppStorage()
        val persistence = FruitMergePersistence(storage)
        val state = FruitMergeState(
            previewLevel = FruitLevel.PLUM,
            score = 400,
            bestScore = 900,
            freeClears = 1,
            freeShakes = 0,
        )

        persistence.checkpoint(state)

        assertEquals(state, persistence.restore())
        assertEquals(900, storage.getLong(BEST_SCORE_KEY))
    }

    @Test
    fun `clear run removes snapshot but retains best score`() = runTest {
        val storage = MutableMiniAppStorage()
        val persistence = FruitMergePersistence(storage)
        persistence.checkpoint(FruitMergeState(score = 120, bestScore = 240))

        persistence.clearRun()

        assertNull(storage.readSnapshot(SNAPSHOT_KEY, FruitMergeSnapshotSpec))
        assertEquals(240, persistence.restore().bestScore)
    }
}
