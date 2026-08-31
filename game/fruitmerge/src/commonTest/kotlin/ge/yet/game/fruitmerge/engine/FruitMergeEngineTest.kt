package ge.yet.game.fruitmerge.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FruitMergeEngineTest {
    private val engine = FruitMergeEngine()

    @Test
    fun `equal contact merges once and scores the created level`() {
        val next = engine.step(stateWithTouching(FruitLevel.RASPBERRY, FruitLevel.RASPBERRY), 1f / 60f)

        assertEquals(listOf(FruitLevel.STRAWBERRY), next.bodies.map(FruitBody::level))
        assertEquals(FruitLevel.STRAWBERRY.mergeScore, next.score)
        assertTrue(next.bodies.single().hasJoinedPile)
    }

    @Test
    fun `record marker requires a strict improvement and resets for a new run`() {
        val score = FruitLevel.STRAWBERRY.mergeScore
        val tied = engine.step(
            stateWithTouching(FruitLevel.RASPBERRY, FruitLevel.RASPBERRY).copy(bestScore = score),
            1f / 60f,
        )
        val improved = engine.step(
            stateWithTouching(FruitLevel.RASPBERRY, FruitLevel.RASPBERRY).copy(bestScore = score - 1),
            1f / 60f,
        )

        assertFalse(tied.bestImprovedInRun)
        assertTrue(improved.bestImprovedInRun)
        assertFalse(engine.newRun(improved).bestImprovedInRun)
    }

    @Test
    fun `two melons disappear for maximum award`() {
        val next = engine.step(stateWithTouching(FruitLevel.WATERMELON, FruitLevel.WATERMELON), 1f / 60f)

        assertTrue(next.bodies.isEmpty())
        assertEquals(FruitLevel.WATERMELON.mergeScore * 2, next.score)
    }

    @Test
    fun `brief overflow resets but sustained overflow ends run`() {
        var state = stateWithBodyAboveDangerLine()
        repeat(89) {
            state = engine.step(state.pinnedAboveDangerLine(), 1f / 60f)
        }
        assertEquals(RunPhase.PLAYING, state.phase)

        state = engine.step(state.pinnedAboveDangerLine(), 1f / 60f)

        assertEquals(RunPhase.RESULT, state.phase)
    }

    @Test
    fun `clear consumes a free use only after a valid target`() {
        val original = stateWithBody(FruitLevel.APPLE)

        val rejected = engine.clear(original, bodyId = 99)
        val cleared = engine.clear(original, bodyId = 1)

        assertEquals(ActionRejection.BODY_NOT_FOUND, rejected.rejection)
        assertEquals(5, rejected.state.freeClears)
        assertNull(cleared.rejection)
        assertEquals(4, cleared.state.freeClears)
        assertTrue(cleared.state.bodies.isEmpty())
    }

    @Test
    fun `paid clear bypasses an exhausted free count`() {
        val state = stateWithBody(FruitLevel.APPLE).copy(freeClears = 0)

        assertEquals(ActionRejection.NO_FREE_USE, engine.beginClear(state).rejection)
        assertNull(engine.beginClear(state, paid = true).rejection)
        assertEquals(0, engine.clear(state, bodyId = 1, paid = true).state.freeClears)
    }

    @Test
    fun `five valid clears are free and the sixth requires a gate`() {
        var state = FruitMergeState(
            bodies = (1L..6L).map { id ->
                FruitBody(id, FruitLevel.BLUEBERRY, Vec2(0.08f + id * 0.12f, 0.8f))
            },
            nextBodyId = 7,
        )
        repeat(5) { index ->
            val result = engine.clear(state, bodyId = index + 1L)
            assertNull(result.rejection)
            state = result.state
        }

        assertEquals(0, state.freeClears)
        assertEquals(ActionRejection.NO_FREE_USE, engine.beginClear(state).rejection)
        assertNull(engine.beginClear(state, paid = true).rejection)
    }

    @Test
    fun `three settled shakes are free and the fourth requires a gate`() {
        var state = stateWithBody(FruitLevel.APPLE)
        repeat(3) {
            val result = engine.shake(state)
            assertNull(result.rejection)
            state = generateSequence(result.state) { current -> engine.step(current, 1f / 60f) }
                .drop(FruitMergeEngine.SHAKE_DURATION_STEPS)
                .first()
        }

        assertEquals(0, state.freeShakes)
        assertEquals(ActionRejection.NO_FREE_USE, engine.shake(state).rejection)
        assertNull(engine.shake(state, paid = true).rejection)
    }

    @Test
    fun `accepted drop starts cooldown and second drop does not advance rng`() {
        val first = engine.drop(FruitMergeState()).state

        val second = engine.drop(first)

        assertEquals(ActionRejection.DROP_COOLDOWN, second.rejection)
        assertEquals(first, second.state)
        assertEquals(FruitMergeEngine.DROP_COOLDOWN_SECONDS, first.dropCooldownSeconds)
    }

    @Test
    fun `drop promotes the queued fruit and deterministically refills next`() {
        val original = FruitMergeState(
            previewLevel = FruitLevel.BLUEBERRY,
            nextPreviewLevel = FruitLevel.STRAWBERRY,
            random = RandomState(19L),
        )

        val dropped = engine.drop(original).state

        assertEquals(FruitLevel.BLUEBERRY, dropped.bodies.single().level)
        assertFalse(dropped.bodies.single().hasJoinedPile)
        assertEquals(FruitLevel.STRAWBERRY, dropped.previewLevel)
        assertTrue(dropped.nextPreviewLevel in FruitLevel.spawnable)
        assertNotEquals(original.random, dropped.random)
    }

    @Test
    fun `fixed steps make drop ready after cooldown`() {
        val dropped = engine.drop(FruitMergeState()).state
        val ready = generateSequence(dropped) { state -> engine.step(state, 1f / 60f) }
            .drop(27)
            .first()

        assertEquals(0f, ready.dropCooldownSeconds)
        assertNull(engine.drop(ready).rejection)
    }

    @Test
    fun `shake runs for a bounded interval and consumes one free use`() {
        val moving = stateWithBody(FruitLevel.APPLE).copy(
            bodies = listOf(
                FruitBody(
                    id = 1,
                    level = FruitLevel.APPLE,
                    position = Vec2(0.5f, 0.8f),
                    velocity = Vec2(0.2f, 0.1f),
                ),
            ),
        )

        val result = engine.shake(moving)

        assertNull(result.rejection)
        assertEquals(moving.freeShakes - 1, result.state.freeShakes)
        assertEquals(FruitMergeEngine.SHAKE_DURATION_STEPS, result.state.shakeStepsRemaining)

        val firstStep = engine.step(result.state, 1f / 60f)
        assertNotEquals(moving.bodies, firstStep.bodies)

        val completed = generateSequence(firstStep) { state -> engine.step(state, 1f / 60f) }
            .drop(FruitMergeEngine.SHAKE_DURATION_STEPS - 1)
            .first()
        assertEquals(0, completed.shakeStepsRemaining)
    }

    @Test
    fun `shake lasts two point two seconds with eleven sustained pulses`() {
        assertEquals(132, FruitMergeEngine.SHAKE_DURATION_STEPS)
        assertEquals(12, FruitMergeEngine.SHAKE_IMPULSE_INTERVAL_STEPS)

        val body = FruitBody(1, FruitLevel.APPLE, Vec2(0.5f, 0.45f))
        val baseline = FruitMergeState(bodies = listOf(body), nextBodyId = 2)
        val withoutImpulse = engine.step(baseline, 1f / 60f).bodies.single().velocity
        val firstPulse = engine.step(
            baseline.copy(shakeStepsRemaining = FruitMergeEngine.SHAKE_DURATION_STEPS),
            1f / 60f,
        ).bodies.single().velocity
        val lastPulse = engine.step(
            baseline.copy(shakeStepsRemaining = FruitMergeEngine.SHAKE_IMPULSE_INTERVAL_STEPS),
            1f / 60f,
        ).bodies.single().velocity

        val firstStrength = (firstPulse - withoutImpulse).length()
        val lastStrength = (lastPulse - withoutImpulse).length()
        assertTrue(firstStrength > 0.45f)
        assertTrue(lastStrength > firstStrength * 0.35f)
    }

    @Test
    fun `active shake rejects duplicate without consuming a use or advancing rng`() {
        val active = engine.shake(stateWithBody(FruitLevel.APPLE)).state

        val duplicate = engine.shake(active)

        assertEquals(ActionRejection.SHAKE_ACTIVE, duplicate.rejection)
        assertEquals(active, duplicate.state)
    }

    @Test
    fun `shake impulses are repeated and deterministic`() {
        val active = engine.shake(stateWithBody(FruitLevel.APPLE)).state
        val first = engine.step(active, 1f / 60f)
        val beforeSecondImpulse = generateSequence(first) { state -> engine.step(state, 1f / 60f) }
            .drop(FruitMergeEngine.SHAKE_IMPULSE_INTERVAL_STEPS - 1)
            .first()
        val secondImpulse = engine.step(beforeSecondImpulse, 1f / 60f)

        assertNotEquals(beforeSecondImpulse.random, secondImpulse.random)
        assertEquals(
            secondImpulse,
            engine.step(
                generateSequence(first) { state -> engine.step(state, 1f / 60f) }
                    .drop(FruitMergeEngine.SHAKE_IMPULSE_INTERVAL_STEPS - 1)
                    .first(),
                1f / 60f,
            ),
        )
    }

    @Test
    fun `drop chooses only spawnable levels and remains in bounds`() {
        var state = FruitMergeState(previewX = -10f, previewLevel = FruitLevel.MANDARIN)
        repeat(30) {
            state = engine.movePreview(state, if (it % 2 == 0) -2f else 2f)
            val dropped = engine.drop(state)
            assertNull(dropped.rejection)
            val body = dropped.state.bodies.last()
            assertTrue(body.level in FruitLevel.spawnable)
            assertTrue(body.position.x in body.level.radius..(1f - body.level.radius))
            state = dropped.state.copy(bodies = emptyList(), dropCooldownSeconds = 0f)
        }
    }

    private fun stateWithTouching(first: FruitLevel, second: FruitLevel) = FruitMergeState(
        bodies = listOf(
            FruitBody(1, first, Vec2(0.5f - first.radius, 0.75f)),
            FruitBody(2, second, Vec2(0.5f + second.radius, 0.75f)),
        ),
        nextBodyId = 3,
    )

    private fun stateWithBody(level: FruitLevel) = FruitMergeState(
        bodies = listOf(FruitBody(1, level, Vec2(0.5f, 0.8f))),
        nextBodyId = 2,
    )

    private fun stateWithBodyAboveDangerLine() = FruitMergeState(
        bodies = listOf(
            FruitBody(
                id = 1,
                level = FruitLevel.WATERMELON,
                position = Vec2(0.5f, FruitMergeEngine.DANGER_Y - 0.12f),
            ),
        ),
        nextBodyId = 2,
    )

    private fun FruitMergeState.pinnedAboveDangerLine(): FruitMergeState = copy(
        bodies = bodies.map { body ->
            body.copy(
                position = Vec2(0.5f, FruitMergeEngine.DANGER_Y - 0.12f),
                velocity = Vec2.ZERO,
            )
        },
    )
}
