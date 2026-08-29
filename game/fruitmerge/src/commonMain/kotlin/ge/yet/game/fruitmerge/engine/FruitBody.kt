package ge.yet.game.fruitmerge.engine

import kotlin.math.sqrt

data class Vec2(
    val x: Float,
    val y: Float,
) {
    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)
    operator fun times(scale: Float): Vec2 = Vec2(x * scale, y * scale)
    operator fun div(scale: Float): Vec2 = Vec2(x / scale, y / scale)

    fun dot(other: Vec2): Float = x * other.x + y * other.y
    fun lengthSquared(): Float = dot(this)
    fun length(): Float = sqrt(lengthSquared())
    fun isFinite(): Boolean = x.isFinite() && y.isFinite()

    companion object {
        val ZERO: Vec2 = Vec2(0f, 0f)
    }
}

data class FruitBody(
    val id: Long,
    val level: FruitLevel,
    val position: Vec2,
    val velocity: Vec2 = Vec2.ZERO,
    val angle: Float = 0f,
    val angularVelocity: Float = 0f,
    val impact: Float = 0f,
)
