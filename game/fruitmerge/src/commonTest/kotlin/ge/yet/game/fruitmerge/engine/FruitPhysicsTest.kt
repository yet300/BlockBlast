package ge.yet.game.fruitmerge.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FruitPhysicsTest {
    @Test
    fun `fixed step applies gravity and keeps bodies inside walls and floor`() {
        val body = FruitBody(
            id = 1,
            level = FruitLevel.RASPBERRY,
            position = Vec2(0.01f, 0.90f),
            velocity = Vec2(-2f, 3f),
        )

        val actual = FruitPhysics().step(listOf(body), 1f / 60f).bodies.single()

        assertTrue(actual.position.x >= actual.level.radius)
        assertTrue(actual.position.y <= FruitPhysics.FLOOR_Y - actual.level.radius)
    }

    @Test
    fun `floor contact joins the pile`() {
        val body = FruitBody(
            id = 1,
            level = FruitLevel.BLUEBERRY,
            position = Vec2(0.5f, 0.99f),
        )

        val actual = FruitPhysics().step(listOf(body), 1f / 60f).bodies.single()

        assertTrue(actual.hasJoinedPile)
    }

    @Test
    fun `free fall does not mark a fruit as joined to pile`() {
        val body = FruitBody(
            id = 1,
            level = FruitLevel.BLUEBERRY,
            position = Vec2(0.5f, 0.20f),
        )

        val actual = FruitPhysics().step(listOf(body), 1f / 60f).bodies.single()

        assertFalse(actual.hasJoinedPile)
    }

    @Test
    fun `side wall contact during fall does not join the pile`() {
        val body = FruitBody(
            id = 1,
            level = FruitLevel.BLUEBERRY,
            position = Vec2(0.01f, 0.20f),
            velocity = Vec2(-2f, 0f),
        )

        val actual = FruitPhysics().step(listOf(body), 1f / 60f).bodies.single()

        assertFalse(actual.hasJoinedPile)
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
        assertTrue(result.bodies.all(FruitBody::hasJoinedPile))
    }

    @Test
    fun `blueberry rebounds more than peach from the floor`() {
        val blueberry = floorImpact(FruitLevel.BLUEBERRY)
        val peach = floorImpact(FruitLevel.PEACH)

        assertTrue(blueberry.velocity.y < peach.velocity.y)
    }

    @Test
    fun `strawberry wall grip is temporary`() {
        var body = FruitBody(
            id = 1,
            level = FruitLevel.STRAWBERRY,
            position = Vec2(0.01f, 0.30f),
            velocity = Vec2(-1f, 0.4f),
        )
        body = FruitPhysics().step(listOf(body), 1f / 60f).bodies.single()
        assertTrue(body.wallGripSecondsRemaining > 0f)

        repeat(31) { body = FruitPhysics().step(listOf(body), 1f / 60f).bodies.single() }
        assertEquals(0f, body.wallGripSecondsRemaining)
    }

    @Test
    fun `side wall contact does not brake a falling strawberry`() {
        val body = FruitBody(
            id = 1,
            level = FruitLevel.STRAWBERRY,
            position = Vec2(0.01f, 0.30f),
            velocity = Vec2(-1f, 1f),
        )

        val actual = FruitPhysics().step(listOf(body), 1f / 60f).bodies.single()

        assertTrue(actual.velocity.y > 0.95f)
    }

    @Test
    fun `side contact preserves downward motion instead of making fruit sticky`() {
        val raspberry = FruitLevel.RASPBERRY
        val blueberry = FruitLevel.BLUEBERRY
        val bodies = listOf(
            FruitBody(1, raspberry, Vec2(0.30f, 0.30f), velocity = Vec2(0f, 1f)),
            FruitBody(
                id = 2,
                level = blueberry,
                position = Vec2(0.30f + raspberry.radius + blueberry.radius - 0.001f, 0.30f),
            ),
        )

        val actual = FruitPhysics().step(bodies, 1f / 60f).bodies.first()

        assertTrue(actual.velocity.y > 0.95f)
    }

    @Test
    fun `mandarin rolls farther than raspberry on the floor`() {
        val mandarin = floorImpact(FruitLevel.MANDARIN, velocity = Vec2(1f, 1f))
        val raspberry = floorImpact(FruitLevel.RASPBERRY, velocity = Vec2(1f, 1f))

        assertTrue(mandarin.velocity.x > raspberry.velocity.x)
    }

    @Test
    fun `heavy apple transfers more forward velocity than soft peach`() {
        val appleTransfer = forwardTransferFrom(FruitLevel.APPLE)
        val peachTransfer = forwardTransferFrom(FruitLevel.PEACH)

        assertTrue(appleTransfer > peachTransfer)
    }

    @Test
    fun `lime receives more spin than mandarin from an off center contact`() {
        val limeSpin = offCenterSpinFor(FruitLevel.LIME)
        val mandarinSpin = offCenterSpinFor(FruitLevel.MANDARIN)

        assertTrue(limeSpin > mandarinSpin)
    }

    @Test
    fun `pear contact adds deterministic signed balance torque`() {
        val pear = FruitBody(1, FruitLevel.PEAR, Vec2(0.40f, 0.45f))
        val blueberry = FruitBody(
            id = 2,
            level = FruitLevel.BLUEBERRY,
            position = Vec2(0.40f + FruitLevel.PEAR.radius + FruitLevel.BLUEBERRY.radius - 0.001f, 0.45f),
        )

        val actual = FruitPhysics().step(listOf(pear, blueberry), 1f / 60f).bodies.first()

        assertTrue(actual.angularVelocity > 0f)
    }

    @Test
    fun `watermelon shock fires once and remains bounded`() {
        val source = FruitBody(
            id = 1,
            level = FruitLevel.WATERMELON,
            position = Vec2(0.5f, FruitPhysics.FLOOR_Y - FruitLevel.WATERMELON.radius + 0.01f),
            velocity = Vec2(0f, 1.2f),
            hasJoinedPile = true,
            shockAvailable = true,
        )
        val neighbor = FruitBody(
            id = 2,
            level = FruitLevel.BLUEBERRY,
            position = Vec2(0.76f, 0.74f),
            hasJoinedPile = true,
        )

        val first = FruitPhysics().step(listOf(source, neighbor), 1f / 60f).bodies

        assertFalse(first.first { it.id == 1L }.shockAvailable)
        assertTrue(first.first { it.id == 2L }.velocity.x > 0f)
        assertTrue(first.all { it.velocity.length() <= 3.5f })

        val second = FruitPhysics().step(first, 1f / 60f).bodies
        assertFalse(second.first { it.id == 1L }.shockAvailable)
    }

    private fun floorImpact(
        level: FruitLevel,
        velocity: Vec2 = Vec2(0f, 1f),
    ): FruitBody = FruitPhysics().step(
        input = listOf(
            FruitBody(
                id = 1,
                level = level,
                position = Vec2(0.5f, FruitPhysics.FLOOR_Y - level.radius + 0.01f),
                velocity = velocity,
            ),
        ),
        dt = 1f / 60f,
    ).bodies.single()

    private fun forwardTransferFrom(level: FruitLevel): Float {
        val target = FruitLevel.BLUEBERRY
        val sourceX = 0.30f
        val bodies = listOf(
            FruitBody(1, level, Vec2(sourceX, 0.35f), velocity = Vec2(1f, 0f)),
            FruitBody(
                id = 2,
                level = target,
                position = Vec2(sourceX + level.radius + target.radius - 0.001f, 0.35f),
            ),
        )
        return FruitPhysics().step(bodies, 1f / 60f).bodies[1].velocity.x
    }

    private fun offCenterSpinFor(level: FruitLevel): Float {
        val target = FruitLevel.BLUEBERRY
        val normal = Vec2(0.8f, 0.6f)
        val distance = level.radius + target.radius - 0.001f
        val bodies = listOf(
            FruitBody(1, level, Vec2(0.30f, 0.30f), velocity = Vec2(1f, 0f)),
            FruitBody(2, target, Vec2(0.30f, 0.30f) + normal * distance),
        )
        return kotlin.math.abs(
            FruitPhysics().step(bodies, 1f / 60f).bodies.first().angularVelocity,
        )
    }

}
