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
        val bodies = input.take(MAX_BODIES).mapTo(ArrayList(MAX_BODIES)) { body ->
            integrate(body, dt)
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

        return PhysicsResult(
            bodies = bodies.map { body -> body.copy(impact = body.impact * IMPACT_DECAY) },
            contacts = contacts,
            candidatePairCount = candidatePairCount,
        )
    }

    private fun integrate(body: FruitBody, dt: Float): FruitBody {
        val velocity = Vec2(
            x = (body.velocity.x * AIR_DAMPING).coerceIn(-MAX_SPEED, MAX_SPEED),
            y = (body.velocity.y * AIR_DAMPING + GRAVITY * dt).coerceIn(-MAX_SPEED, MAX_SPEED),
        )
        return body.copy(
            position = body.position + velocity * dt,
            velocity = velocity,
            angle = body.angle + body.angularVelocity * dt,
            angularVelocity = (body.angularVelocity * ANGULAR_DAMPING).coerceIn(-MAX_ANGULAR_SPEED, MAX_ANGULAR_SPEED),
        )
    }

    private fun constrainToContainer(body: FruitBody): FruitBody {
        val radius = body.level.radius
        var x = body.position.x
        var y = body.position.y
        var velocityX = body.velocity.x
        var velocityY = body.velocity.y
        var angularVelocity = body.angularVelocity
        var impact = body.impact

        if (x < radius) {
            x = radius
            impact = max(impact, abs(velocityX))
            velocityX = abs(velocityX) * WALL_RESTITUTION
        } else if (x > 1f - radius) {
            x = 1f - radius
            impact = max(impact, abs(velocityX))
            velocityX = -abs(velocityX) * WALL_RESTITUTION
        }
        val floor = FLOOR_Y - radius
        if (y > floor) {
            y = floor
            impact = max(impact, abs(velocityY))
            velocityY = -abs(velocityY) * WALL_RESTITUTION
            if (abs(velocityY) < REST_SPEED) velocityY = 0f
            velocityX *= FLOOR_FRICTION
            angularVelocity *= FLOOR_FRICTION
        }

        return body.copy(
            position = Vec2(x, y),
            velocity = Vec2(velocityX, velocityY),
            angularVelocity = angularVelocity,
            impact = impact.coerceAtMost(MAX_IMPACT),
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
        val inverseMassFirst = 1f / first.level.mass
        val inverseMassSecond = 1f / second.level.mass
        val inverseMassSum = inverseMassFirst + inverseMassSecond
        val penetration = (minimumDistance - distance).coerceAtLeast(0f)
        val correction = normal * (penetration * POSITION_CORRECTION / inverseMassSum)

        var firstVelocity = first.velocity
        var secondVelocity = second.velocity
        val relativeNormalVelocity = (secondVelocity - firstVelocity).dot(normal)
        var impulseMagnitude = 0f
        if (relativeNormalVelocity < 0f) {
            impulseMagnitude = -(1f + CONTACT_RESTITUTION) * relativeNormalVelocity / inverseMassSum
            val impulse = normal * impulseMagnitude
            firstVelocity = firstVelocity - impulse * inverseMassFirst
            secondVelocity = secondVelocity + impulse * inverseMassSecond
        }

        bodies[pair.firstIndex] = first.copy(
            position = first.position - correction * inverseMassFirst,
            velocity = firstVelocity,
            impact = max(first.impact, impulseMagnitude),
        )
        bodies[pair.secondIndex] = second.copy(
            position = second.position + correction * inverseMassSecond,
            velocity = secondVelocity,
            impact = max(second.impact, impulseMagnitude),
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
        private const val CONTACT_RESTITUTION: Float = 0.08f
        private const val FLOOR_FRICTION: Float = 0.78f
        private const val POSITION_CORRECTION: Float = 0.82f
        private const val CONTACT_EPSILON: Float = 0.000_01f
        private const val CONTACT_SLOP: Float = 0.000_1f
        private const val REST_SPEED: Float = 0.018f
        private const val MAX_SPEED: Float = 3.5f
        private const val MAX_ANGULAR_SPEED: Float = 8f
        private const val MAX_IMPACT: Float = 2f
        private const val IMPACT_DECAY: Float = 0.88f
    }
}
