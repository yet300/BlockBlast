package ge.yet.blokblast.domain.model

import kotlinx.serialization.Serializable

/**
 * Immutable shape definition.
 *
 * @param id stable shape id (string from [ge.yet.blokblast.domain.engine.ShapeCatalog]).
 * @param cells relative coordinates from the local top-left origin (0,0).
 *              Shapes are NEVER rotated — orientation is fixed.
 */
@Serializable
data class Polyomino(
    val id: String,
    val cells: List<Position>,
) {
    val size: Int get() = cells.size
    val width: Int get() = (cells.maxOfOrNull { it.x } ?: -1) + 1
    val height: Int get() = (cells.maxOfOrNull { it.y } ?: -1) + 1
}
