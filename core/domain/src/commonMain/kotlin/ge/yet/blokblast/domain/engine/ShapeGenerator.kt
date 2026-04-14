package ge.yet.blokblast.domain.engine

import dev.zacsweers.metro.Inject
import ge.yet.blokblast.domain.model.Polyomino
import kotlin.random.Random

/** Strategy interface — easy to swap in tests with a deterministic implementation. */
interface ShapeGenerator {
    fun nextTray(seed: Long? = null): List<Polyomino>
    fun smallReviveTray(): List<Polyomino>
}

/**
 * Weighted generator — guarantees a fair mix of small/medium/large shapes
 * so the player never gets three 3x3 blocks in a row.
 */
@Inject
internal class WeightedShapeGenerator : ShapeGenerator {

    private val defaultRandom = Random.Default

    override fun nextTray(seed: Long?): List<Polyomino> {
        val rnd = seed?.let { Random(it) } ?: defaultRandom
        // Distribution: 1 small, 1 medium, 1 anything (weighted).
        return listOf(
            ShapeCatalog.SMALL.random(rnd),
            ShapeCatalog.MEDIUM.random(rnd),
            pickWeighted(rnd),
        ).shuffled(rnd)
    }

    override fun smallReviveTray(): List<Polyomino> = listOf(
        ShapeCatalog.SMALL[0], // 1x1
        ShapeCatalog.SMALL[1], // 1x2
        ShapeCatalog.SMALL[2], // 2x1
    )

    private fun pickWeighted(rnd: Random): Polyomino {
        // 30% small, 45% medium, 25% large
        return when (rnd.nextInt(100)) {
            in 0..29 -> ShapeCatalog.SMALL.random(rnd)
            in 30..74 -> ShapeCatalog.MEDIUM.random(rnd)
            else -> ShapeCatalog.LARGE.random(rnd)
        }
    }
}
