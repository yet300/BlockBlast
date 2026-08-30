package ge.yet.game.fruitmerge.persistence

import ge.yet.game.fruitmerge.engine.FruitBody
import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.engine.FruitMergeEngine
import ge.yet.game.fruitmerge.engine.FruitMergeState
import ge.yet.game.fruitmerge.engine.MAX_BODIES
import ge.yet.game.fruitmerge.engine.RandomState
import ge.yet.game.fruitmerge.engine.RunPhase
import ge.yet.game.fruitmerge.engine.TargetingMode
import ge.yet.game.fruitmerge.engine.Vec2
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max

@Serializable
internal data class FruitBodySnapshot(
    val id: Long,
    val level: String,
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val angle: Float,
    val angularVelocity: Float,
)

@Serializable
internal data class FruitMergeSnapshot(
    val bodies: List<FruitBodySnapshot>,
    val previewLevel: String,
    val previewX: Float,
    val randomBits: Long,
    val nextBodyId: Long,
    val score: Long,
    val freeClears: Int,
    val freeShakes: Int,
    val dangerSeconds: Float,
    val graceSeconds: Float,
    val runOrdinal: Long,
    val phase: String,
    val shakeStepsRemaining: Int = 0,
) {
    fun toState(bestScore: Long): FruitMergeState {
        require(bodies.size <= MAX_BODIES)
        require(score >= 0L && bestScore >= 0L)
        require(freeClears in 0..FruitMergeState.FREE_CLEAR_COUNT)
        require(freeShakes in 0..FruitMergeState.FREE_SHAKE_COUNT)
        require(dangerSeconds.isFinite() && dangerSeconds in 0f..MAX_DANGER_SECONDS)
        require(graceSeconds.isFinite() && graceSeconds in 0f..MAX_GRACE_SECONDS)
        require(shakeStepsRemaining in 0..FruitMergeEngine.SHAKE_DURATION_STEPS)
        require(runOrdinal > 0L)
        val restoredPreview = requireNotNull(FruitLevel.entries.firstOrNull { it.name == previewLevel })
        require(restoredPreview in FruitLevel.spawnable)
        require(previewX.isFinite())
        require(previewX in restoredPreview.radius - WORLD_TOLERANCE..1f - restoredPreview.radius + WORLD_TOLERANCE)
        val restoredPhase = requireNotNull(RunPhase.entries.firstOrNull { it.name == phase })
        val ids = HashSet<Long>(bodies.size)
        val restoredBodies = bodies.map { snapshot -> snapshot.toBody().also { require(ids.add(it.id)) } }
        val maximumId = restoredBodies.maxOfOrNull(FruitBody::id) ?: 0L
        require(nextBodyId > maximumId)

        return FruitMergeState(
            bodies = restoredBodies,
            previewLevel = restoredPreview,
            previewX = previewX.coerceIn(restoredPreview.radius, 1f - restoredPreview.radius),
            random = RandomState(randomBits),
            nextBodyId = nextBodyId,
            score = score,
            bestScore = max(bestScore, score),
            freeClears = freeClears,
            freeShakes = freeShakes,
            dangerSeconds = dangerSeconds,
            graceSeconds = graceSeconds,
            shakeStepsRemaining = shakeStepsRemaining,
            runOrdinal = runOrdinal,
            phase = restoredPhase,
            targetingMode = TargetingMode.NONE,
        )
    }

    companion object {
        fun from(state: FruitMergeState): FruitMergeSnapshot = FruitMergeSnapshot(
            bodies = state.bodies.map(FruitBody::toSnapshot),
            previewLevel = state.previewLevel.name,
            previewX = state.previewX,
            randomBits = state.random.bits,
            nextBodyId = state.nextBodyId,
            score = state.score,
            freeClears = state.freeClears,
            freeShakes = state.freeShakes,
            dangerSeconds = state.dangerSeconds,
            graceSeconds = state.graceSeconds,
            shakeStepsRemaining = state.shakeStepsRemaining,
            runOrdinal = state.runOrdinal,
            phase = state.phase.name,
        )
    }
}

private fun FruitBodySnapshot.toBody(): FruitBody {
    require(id > 0L)
    val restoredLevel = requireNotNull(FruitLevel.entries.firstOrNull { it.name == level })
    require(x.isFinite() && y.isFinite())
    require(velocityX.isFinite() && velocityY.isFinite())
    require(angle.isFinite() && angularVelocity.isFinite())
    require(x in restoredLevel.radius - WORLD_TOLERANCE..1f - restoredLevel.radius + WORLD_TOLERANCE)
    require(y in -MAX_LEVEL_RADIUS - WORLD_TOLERANCE..1f - restoredLevel.radius + WORLD_TOLERANCE)
    require(abs(velocityX) <= MAX_RESTORE_SPEED && abs(velocityY) <= MAX_RESTORE_SPEED)
    require(abs(angularVelocity) <= MAX_RESTORE_ANGULAR_SPEED)
    return FruitBody(
        id = id,
        level = restoredLevel,
        position = Vec2(x, y),
        velocity = Vec2(velocityX, velocityY),
        angle = angle,
        angularVelocity = angularVelocity,
    )
}

private fun FruitBody.toSnapshot(): FruitBodySnapshot = FruitBodySnapshot(
    id = id,
    level = level.name,
    x = position.x,
    y = position.y,
    velocityX = velocity.x,
    velocityY = velocity.y,
    angle = angle,
    angularVelocity = angularVelocity,
)

private const val WORLD_TOLERANCE: Float = 0.02f
private const val MAX_LEVEL_RADIUS: Float = 0.21f
private const val MAX_RESTORE_SPEED: Float = 4f
private const val MAX_RESTORE_ANGULAR_SPEED: Float = 10f
private const val MAX_DANGER_SECONDS: Float = 1.6f
private const val MAX_GRACE_SECONDS: Float = 0.8f
