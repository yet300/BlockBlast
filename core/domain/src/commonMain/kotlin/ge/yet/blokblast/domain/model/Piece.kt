package ge.yet.blokblast.domain.model

import kotlinx.serialization.Serializable

/** A concrete tray instance of a [Polyomino] with a unique runtime id and color. */
@Serializable
data class Piece(
    val pieceId: Long,
    val shape: Polyomino,
    val colorId: Int,
)
