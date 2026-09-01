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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FruitMergeSnapshotTest {
    @Test
    fun `validated snapshot round trips authoritative run fields`() {
        val state = populatedState(freeClears = 2, freeShakes = 1, randomBits = 99)
            .copy(shakeStepsRemaining = 42, bestImprovedInRun = true)
        val restored = FruitMergeSnapshot.from(state).toState(bestScore = state.bestScore)

        assertEquals(state, restored)
        assertTrue(restored.bodies.all(FruitBody::hasJoinedPile))
    }

    @Test
    fun `legacy snapshot defaults to an inactive shake`() {
        val encoded = Json.encodeToString(FruitMergeSnapshot.serializer(), validSnapshot())
            .replace(",\"shakeStepsRemaining\":0", "")

        val decoded = Json.decodeFromString(FruitMergeSnapshot.serializer(), encoded)

        assertEquals(0, decoded.toState(bestScore = 0).shakeStepsRemaining)
    }

    @Test
    fun `legacy fruit names decode to current market identities`() {
        val restored = validSnapshot().copy(
            bodies = listOf(
                validBody(id = 1L).copy(level = "CHERRY", x = 0.3f),
                validBody(id = 2L).copy(level = "MELON", x = 0.7f),
            ),
            previewLevel = "PLUM",
            nextPreviewLevel = "CHERRY",
            nextBodyId = 3L,
        ).toState(bestScore = 0)

        assertEquals(
            listOf(FruitLevel.RASPBERRY, FruitLevel.WATERMELON),
            restored.bodies.map(FruitBody::level),
        )
        assertEquals(FruitLevel.LIME, restored.previewLevel)
        assertEquals(FruitLevel.RASPBERRY, restored.nextPreviewLevel)
    }

    @Test
    fun `legacy settled watermelon does not restore an armed shock`() {
        val restored = validSnapshot().copy(
            bodies = listOf(validBody().copy(level = "MELON")),
        ).toState(bestScore = 0)

        assertFalse(restored.bodies.single().shockAvailable)
        assertEquals(0f, restored.bodies.single().wallGripSecondsRemaining)
    }

    @Test
    fun `trait state round trips through schema two snapshot`() {
        val state = FruitMergeState(
            bodies = listOf(
                FruitBody(
                    id = 1,
                    level = FruitLevel.WATERMELON,
                    position = Vec2(0.5f, 0.75f),
                    hasJoinedPile = true,
                    wallGripSecondsRemaining = 0.2f,
                    shockAvailable = false,
                ),
            ),
            nextBodyId = 2,
        )

        val restored = FruitMergeSnapshot.from(state).toState(bestScore = 0)

        assertEquals(state, restored)
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
        level = FruitLevel.RASPBERRY.name,
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
            FruitBody(
                id = 1,
                level = FruitLevel.LIME,
                position = Vec2(0.42f, 0.74f),
                velocity = Vec2(0.01f, -0.02f),
                hasJoinedPile = true,
            ),
            FruitBody(
                id = 2,
                level = FruitLevel.APPLE,
                position = Vec2(0.63f, 0.79f),
                hasJoinedPile = true,
            ),
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
