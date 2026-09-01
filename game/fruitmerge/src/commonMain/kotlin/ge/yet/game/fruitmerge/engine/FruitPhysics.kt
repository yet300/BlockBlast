package ge.yet.game.fruitmerge.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class PhysicsResult(
    val bodies: List<FruitBody>,
    val contacts: List<BodyPair>,
    val candidatePairCount: Int,
)

class FruitPhysics(
    private val grid: SpatialGrid = SpatialGrid(),
) {
    fun step(input: List<FruitBody>, dt: Float): PhysicsResult {
        require(dt.isFinite() && dt in 0f..MAX_STEP_SECONDS)
        val bodyCount = minOf(input.size, MAX_BODIES)
        val bodies = ArrayList<FruitBody>(bodyCount)
        for (index in 0 until bodyCount) {
            bodies += integrate(input[index], dt)
        }
        val contactKeys = HashSet<Long>(MAX_CANDIDATE_PAIRS)
        val contacts = ArrayList<BodyPair>(MAX_CANDIDATE_PAIRS)
        var candidatePairCount = 0

        repeat(MAX_CONTACT_PASSES) {
            val candidates = grid.candidatePairs(bodies)
            candidatePairCount = max(candidatePairCount, candidates.size)
            for (pair in candidates) {
                if (resolveCirclePair(bodies, pair)) {
                    val firstIndex = minOf(pair.firstIndex, pair.secondIndex)
                    val secondIndex = maxOf(pair.firstIndex, pair.secondIndex)
                    val key = (firstIndex.toLong() shl 32) or secondIndex.toLong()
                    if (contactKeys.add(key) && contacts.size < MAX_CANDIDATE_PAIRS) {
                        contacts += pair
                    }
                }
            }
            bodies.indices.forEach { index ->
                bodies[index] = constrainToContainer(bodies[index])
            }
        }

        applyWatermelonShocks(bodies)

        return PhysicsResult(
            bodies = bodies.map { body -> body.copy(impact = body.impact * IMPACT_DECAY) },
            contacts = contacts,
            candidatePairCount = candidatePairCount,
        )
    }

    private fun applyWatermelonShocks(bodies: MutableList<FruitBody>) {
        for (sourceIndex in bodies.indices) {
            val source = bodies[sourceIndex]
            if (
                source.level != FruitLevel.WATERMELON ||
                !source.shockAvailable ||
                source.impact < WATERMELON_SHOCK_IMPACT_THRESHOLD
            ) {
                continue
            }

            val profile = fruitPhysicsProfile(source.level)
            val reach = source.level.radius * WATERMELON_SHOCK_REACH_MULTIPLIER
            bodies[sourceIndex] = source.copy(shockAvailable = false)
            for (targetIndex in bodies.indices) {
                if (targetIndex == sourceIndex) continue
                val target = bodies[targetIndex]
                val delta = target.position - source.position
                val distance = delta.length().coerceAtLeast(CONTACT_EPSILON)
                if (distance >= reach) continue

                val falloff = 1f - distance / reach
                val impulse = delta / distance * (profile.shockImpulse * falloff)
                bodies[targetIndex] = target.copy(
                    velocity = (target.velocity + impulse).clampLength(MAX_SPEED),
                )
            }
        }
    }

    private fun Vec2.clampLength(maxLength: Float): Vec2 {
        val lengthSquared = lengthSquared()
        return if (lengthSquared > maxLength * maxLength) {
            this * (maxLength / length())
        } else {
            this
        }
    }

    private fun integrate(body: FruitBody, dt: Float): FruitBody {
        val gripSecondsRemaining = (body.wallGripSecondsRemaining - dt).coerceAtLeast(0f)
        val velocity = Vec2(
            x = (body.velocity.x * AIR_DAMPING).coerceIn(-MAX_SPEED, MAX_SPEED),
            y = (
                body.velocity.y * AIR_DAMPING + GRAVITY * dt
            ).coerceIn(-MAX_SPEED, MAX_SPEED),
        )
        return body.copy(
            position = body.position + velocity * dt,
            velocity = velocity,
            angle = body.angle + body.angularVelocity * dt,
            angularVelocity = (body.angularVelocity * ANGULAR_DAMPING).coerceIn(-MAX_ANGULAR_SPEED, MAX_ANGULAR_SPEED),
            wallGripSecondsRemaining = gripSecondsRemaining,
        )
    }

    private fun constrainToContainer(body: FruitBody): FruitBody {
        val profile = fruitPhysicsProfile(body.level)
        val radius = body.level.radius
        var x = body.position.x
        var y = body.position.y
        var velocityX = body.velocity.x
        var velocityY = body.velocity.y
        var angularVelocity = body.angularVelocity
        var impact = body.impact
        var hasJoinedPile = body.hasJoinedPile
        var wallGripSecondsRemaining = body.wallGripSecondsRemaining

        if (x < radius) {
            x = radius
            impact = max(impact, abs(velocityX))
            velocityX = abs(velocityX) * WALL_RESTITUTION
            if (profile.wallGripSeconds > 0f) {
                wallGripSecondsRemaining = max(wallGripSecondsRemaining, profile.wallGripSeconds)
            }
        } else if (x > 1f - radius) {
            x = 1f - radius
            impact = max(impact, abs(velocityX))
            velocityX = -abs(velocityX) * WALL_RESTITUTION
            if (profile.wallGripSeconds > 0f) {
                wallGripSecondsRemaining = max(wallGripSecondsRemaining, profile.wallGripSeconds)
            }
        }
        val floor = FLOOR_Y - radius
        if (y > floor) {
            y = floor
            impact = max(impact, abs(velocityY))
            velocityY = -abs(velocityY) * profile.contactRestitution
            if (abs(velocityY) < REST_SPEED) velocityY = 0f
            velocityX *= profile.floorRetention
            angularVelocity *= profile.floorRetention
            hasJoinedPile = true
        }

        return body.copy(
            position = Vec2(x, y),
            velocity = Vec2(velocityX, velocityY),
            angularVelocity = angularVelocity,
            impact = impact.coerceAtMost(MAX_IMPACT),
            hasJoinedPile = hasJoinedPile,
            wallGripSecondsRemaining = wallGripSecondsRemaining,
        )
    }

    private fun resolveCirclePair(bodies: MutableList<FruitBody>, pair: BodyPair): Boolean {
        val first = bodies[pair.firstIndex]
        val second = bodies[pair.secondIndex]
        val delta = second.position - first.position
        val minimumDistance = first.level.radius + second.level.radius
        val distanceSquared = delta.lengthSquared()
        val contactDistance = minimumDistance + CONTACT_SLOP
        if (distanceSquared > contactDistance * contactDistance) return false

        val distance = sqrt(distanceSquared)
        val normal = if (distance > CONTACT_EPSILON) {
            delta / distance
        } else if (first.id < second.id) {
            Vec2(1f, 0f)
        } else {
            Vec2(-1f, 0f)
        }
        val firstProfile = fruitPhysicsProfile(first.level)
        val secondProfile = fruitPhysicsProfile(second.level)
        val inverseMassFirst = 1f / (first.level.mass * firstProfile.massMultiplier)
        val inverseMassSecond = 1f / (second.level.mass * secondProfile.massMultiplier)
        val inverseMassSum = inverseMassFirst + inverseMassSecond
        val penetration = (minimumDistance - distance).coerceAtLeast(0f)
        val correction = normal * (penetration * POSITION_CORRECTION / inverseMassSum)

        var firstVelocity = first.velocity
        var secondVelocity = second.velocity
        var firstAngularVelocity = first.angularVelocity
        var secondAngularVelocity = second.angularVelocity
        val relativeNormalVelocity = (secondVelocity - firstVelocity).dot(normal)
        var impulseMagnitude = 0f
        if (relativeNormalVelocity < 0f) {
            val restitution = (
                firstProfile.contactRestitution + secondProfile.contactRestitution
            ) * 0.5f
            impulseMagnitude = -(1f + restitution) * relativeNormalVelocity / inverseMassSum
            val impulse = normal * impulseMagnitude
            firstVelocity = firstVelocity - impulse * inverseMassFirst
            secondVelocity = secondVelocity + impulse * inverseMassSecond
        }

        val tangent = Vec2(-normal.y, normal.x)
        val relativeTangentVelocity = (secondVelocity - firstVelocity).dot(tangent)
        if (abs(relativeTangentVelocity) > CONTACT_EPSILON) {
            val spinTransfer = (
                firstProfile.spinTransfer + secondProfile.spinTransfer
            ) * 0.5f
            val angularImpulse = relativeTangentVelocity * spinTransfer
            firstAngularVelocity += angularImpulse
            secondAngularVelocity -= angularImpulse
        }

        firstAngularVelocity += firstProfile.balanceTorque * normal.x
        secondAngularVelocity -= secondProfile.balanceTorque * normal.x
        firstAngularVelocity = firstAngularVelocity.coerceIn(-MAX_ANGULAR_SPEED, MAX_ANGULAR_SPEED)
        secondAngularVelocity = secondAngularVelocity.coerceIn(-MAX_ANGULAR_SPEED, MAX_ANGULAR_SPEED)

        bodies[pair.firstIndex] = first.copy(
            position = first.position - correction * inverseMassFirst,
            velocity = firstVelocity,
            impact = max(first.impact, impulseMagnitude),
            hasJoinedPile = true,
            angularVelocity = firstAngularVelocity,
        )
        bodies[pair.secondIndex] = second.copy(
            position = second.position + correction * inverseMassSecond,
            velocity = secondVelocity,
            impact = max(second.impact, impulseMagnitude),
            hasJoinedPile = true,
            angularVelocity = secondAngularVelocity,
        )
        return true
    }

    companion object {
        const val FLOOR_Y: Float = 1f
        const val MAX_STEP_SECONDS: Float = 1f / 30f

        private const val GRAVITY: Float = 1.65f
        private const val AIR_DAMPING: Float = 0.998f
        private const val ANGULAR_DAMPING: Float = 0.996f
        private const val WALL_RESTITUTION: Float = 0.18f
        private const val POSITION_CORRECTION: Float = 0.82f
        private const val CONTACT_EPSILON: Float = 0.000_01f
        private const val CONTACT_SLOP: Float = 0.000_1f
        private const val REST_SPEED: Float = 0.018f
        private const val MAX_SPEED: Float = 3.5f
        private const val MAX_ANGULAR_SPEED: Float = 8f
        private const val MAX_IMPACT: Float = 2f
        private const val IMPACT_DECAY: Float = 0.88f
        private const val WATERMELON_SHOCK_IMPACT_THRESHOLD: Float = 0.55f
        private const val WATERMELON_SHOCK_REACH_MULTIPLIER: Float = 2.4f
    }
}
