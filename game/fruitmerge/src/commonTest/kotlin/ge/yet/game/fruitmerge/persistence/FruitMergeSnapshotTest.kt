package ge.yet.game.fruitmerge.persistence

import ge.yet.game.fruitmerge.engine.FruitBody
import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.engine.FruitMergeState
import ge.yet.game.fruitmerge.engine.MAX_BODIES
import ge.yet.game.fruitmerge.engine.RandomState
import ge.yet.game.fruitmerge.engine.RunPhase
import ge.yet.game.fruitmerge.engine.Vec2
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class FruitMergeSnapshotTest {
    @Test
    fun `validated snapshot round trips authoritative run fields`() {
        val state = populatedState(freeClears = 2, freeShakes = 1, randomBits = 99)
            .copy(shakeStepsRemaining = 42)

        assertEquals(state, FruitMergeSnapshot.from(state).toState(bestScore = state.bestScore))
    }

    @Test
    fun `legacy snapshot defaults to an inactive shake`() {
        val encoded = Json.encodeToString(FruitMergeSnapshot.serializer(), validSnapshot())
            .replace(",\"shakeStepsRemaining\":0", "")

        val decoded = Json.decodeFromString(FruitMergeSnapshot.serializer(), encoded)

        assertEquals(0, decoded.toState(bestScore = 0).shakeStepsRemaining)
    }

    @Test
    fun `invalid body and impossible counts are rejected`() {
        assertFails { validSnapshot().copy(freeClears = -1).toState(0) }
        assertFails { validSnapshot().copy(shakeStepsRemaining = 136).toState(0) }
        assertFails {
            validSnapshot().copy(
                bodies = listOf(validBody().copy(x = Float.NaN)),
            ).toState(0)
        }
        assertFails {
            validSnapshot().copy(
                bodies = List(MAX_BODIES + 1) { index -> validBody(id = index.toLong() + 1) },
            ).toState(0)
        }
    }

    @Test
    fun `duplicate ids and unknown enum names are rejected`() {
        assertFails {
            validSnapshot().copy(bodies = listOf(validBody(), validBody())).toState(0)
        }
        assertFails { validSnapshot().copy(previewLevel = "PAPAYA").toState(0) }
        assertFails { validSnapshot().copy(phase = "PAUSED").toState(0) }
    }

    private fun validBody(id: Long = 1L) = FruitBodySnapshot(
        id = id,
        level = FruitLevel.CHERRY.name,
        x = 0.5f,
        y = 0.8f,
        velocityX = 0f,
        velocityY = 0f,
        angle = 0f,
        angularVelocity = 0f,
    )

    private fun validSnapshot() = FruitMergeSnapshot(
        bodies = listOf(validBody()),
        previewLevel = FruitLevel.BLUEBERRY.name,
        previewX = 0.5f,
        randomBits = 7L,
        nextBodyId = 2L,
        score = 0L,
        freeClears = 5,
        freeShakes = 3,
        dangerSeconds = 0f,
        graceSeconds = 0f,
        runOrdinal = 1L,
        phase = RunPhase.PLAYING.name,
    )

    private fun populatedState(
        freeClears: Int,
        freeShakes: Int,
        randomBits: Long,
    ) = FruitMergeState(
        bodies = listOf(
            FruitBody(1, FruitLevel.PLUM, Vec2(0.42f, 0.74f), Vec2(0.01f, -0.02f)),
            FruitBody(2, FruitLevel.APPLE, Vec2(0.63f, 0.79f)),
        ),
        previewLevel = FruitLevel.STRAWBERRY,
        nextPreviewLevel = FruitLevel.MANDARIN,
        previewX = 0.37f,
        random = RandomState(randomBits),
        nextBodyId = 3,
        score = 240,
        bestScore = 800,
        freeClears = freeClears,
        freeShakes = freeShakes,
        runOrdinal = 4,
    )
}
