package ge.yet.game.fruitmerge.engine

import kotlin.test.Test
import kotlin.test.assertTrue

class FruitPhysicsTest {
    @Test
    fun `fixed step applies gravity and keeps bodies inside walls and floor`() {
        val body = FruitBody(
            id = 1,
            level = FruitLevel.CHERRY,
            position = Vec2(0.01f, 0.90f),
            velocity = Vec2(-2f, 3f),
        )

        val actual = FruitPhysics().step(listOf(body), 1f / 60f).bodies.single()

        assertTrue(actual.position.x >= actual.level.radius)
        assertTrue(actual.position.y <= FruitPhysics.FLOOR_Y - actual.level.radius)
    }

    @Test
    fun `overlapping circles separate without non finite values`() {
        val bodies = listOf(
            FruitBody(1, FruitLevel.APPLE, Vec2(0.50f, 0.50f)),
            FruitBody(2, FruitLevel.APPLE, Vec2(0.51f, 0.50f)),
        )

        val result = FruitPhysics().step(bodies, 1f / 60f)
        val distance = (result.bodies[1].position - result.bodies[0].position).length()

        assertTrue(distance >= FruitLevel.APPLE.radius * 1.95f)
        assertTrue(result.bodies.all { it.position.isFinite() && it.velocity.isFinite() })
    }
}
