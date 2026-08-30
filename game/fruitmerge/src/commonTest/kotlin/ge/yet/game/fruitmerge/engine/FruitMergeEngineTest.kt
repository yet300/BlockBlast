package ge.yet.game.fruitmerge.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FruitMergeEngineTest {
    private val engine = FruitMergeEngine()

    @Test
    fun `equal contact merges once and scores the created level`() {
        val next = engine.step(stateWithTouching(FruitLevel.CHERRY, FruitLevel.CHERRY), 1f / 60f)

        assertEquals(listOf(FruitLevel.STRAWBERRY), next.bodies.map(FruitBody::level))
        assertEquals(FruitLevel.STRAWBERRY.mergeScore, next.score)
    }

    @Test
    fun `two melons disappear for maximum award`() {
        val next = engine.step(stateWithTouching(FruitLevel.MELON, FruitLevel.MELON), 1f / 60f)

        assertTrue(next.bodies.isEmpty())
        assertEquals(FruitLevel.MELON.mergeScore * 2, next.score)
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
            state = result.state.copy(
                bodies = result.state.bodies.map { body ->
                    body.copy(velocity = Vec2.ZERO, angularVelocity = 0f)
                },
            )
        }

        assertEquals(0, state.freeShakes)
        assertEquals(ActionRejection.NO_FREE_USE, engine.shake(state).rejection)
        assertNull(engine.shake(state, paid = true).rejection)
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
            state = dropped.state.copy(bodies = emptyList())
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
                level = FruitLevel.MELON,
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
