package ge.yet.game.fruitmerge.engine

import kotlin.math.floor

internal const val MAX_BODIES: Int = 80
internal const val MAX_CONTACT_PASSES: Int = 4
internal const val MAX_CANDIDATE_PAIRS: Int = 960

data class BodyPair(
    val firstIndex: Int,
    val secondIndex: Int,
)

class SpatialGrid {
    private val buckets = HashMap<Int, MutableList<Int>>(128)
    private val pairKeys = HashSet<Long>(MAX_CANDIDATE_PAIRS)
    private val pairs = ArrayList<BodyPair>(MAX_CANDIDATE_PAIRS)
    private val pairPool = arrayOfNulls<BodyPair>(MAX_BODIES * MAX_BODIES)

    fun candidatePairs(bodies: List<FruitBody>): List<BodyPair> {
        buckets.values.forEach(MutableList<Int>::clear)
        pairKeys.clear()
        pairs.clear()

        val bodyCount = minOf(bodies.size, MAX_BODIES)
        for (index in 0 until bodyCount) {
            val body = bodies[index]
            val radius = body.level.radius
            val minX = cell(body.position.x - radius)
            val maxX = cell(body.position.x + radius)
            val minY = cell(body.position.y - radius)
            val maxY = cell(body.position.y + radius)
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    buckets.getOrPut(key(x, y)) { ArrayList(8) }.add(index)
                }
            }
        }

        bucketLoop@ for (bucket in buckets.values) {
            for (firstOffset in 0 until bucket.lastIndex) {
                for (secondOffset in firstOffset + 1 until bucket.size) {
                    val first = minOf(bucket[firstOffset], bucket[secondOffset])
                    val second = maxOf(bucket[firstOffset], bucket[secondOffset])
                    val pairKey = (first.toLong() shl 32) or second.toLong()
                    if (pairKeys.add(pairKey)) {
                        pairs += pooledPair(first, second)
                        if (pairs.size == MAX_CANDIDATE_PAIRS) break@bucketLoop
                    }
                }
            }
        }

        pairs.sortWith(
            compareBy<BodyPair> { pair -> minOf(bodies[pair.firstIndex].id, bodies[pair.secondIndex].id) }
                .thenBy { pair -> maxOf(bodies[pair.firstIndex].id, bodies[pair.secondIndex].id) },
        )
        return pairs
    }

    private fun cell(value: Float): Int = floor(value / CELL_SIZE).toInt()
    private fun key(x: Int, y: Int): Int = (x * 73_856_093) xor (y * 19_349_663)
    private fun pooledPair(first: Int, second: Int): BodyPair {
        val index = first * MAX_BODIES + second
        return pairPool[index] ?: BodyPair(first, second).also { pairPool[index] = it }
    }

    private companion object {
        const val CELL_SIZE: Float = 0.12f
    }
}
