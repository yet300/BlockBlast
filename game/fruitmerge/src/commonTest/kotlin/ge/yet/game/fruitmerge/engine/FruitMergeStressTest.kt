package ge.yet.game.fruitmerge.engine

import kotlin.test.Test
import kotlin.test.assertTrue

class FruitMergeStressTest {
    @Test
    fun `one simulated minute remains finite and bounded`() {
        val engine = FruitMergeEngine()
        var state = FruitMergeState(
            bodies = List(MAX_BODIES) { index ->
                FruitBody(
                    id = index.toLong() + 1,
                    level = FruitLevel.BLUEBERRY,
                    position = Vec2(
                        x = 0.05f + (index % 10) * 0.095f,
                        y = 0.20f + (index / 10) * 0.095f,
                    ),
                )
            },
            nextBodyId = MAX_BODIES.toLong() + 1,
            graceSeconds = 60f,
        )

        repeat(60 * 60) { state = engine.step(state, 1f / 60f) }

        assertTrue(state.bodies.size <= MAX_BODIES)
        assertTrue(state.bodies.all { it.position.isFinite() && it.velocity.isFinite() })
        assertTrue(engine.diagnostics.maxCandidatePairs <= MAX_CANDIDATE_PAIRS)
    }
}
