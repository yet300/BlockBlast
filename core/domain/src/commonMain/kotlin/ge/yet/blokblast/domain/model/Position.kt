package ge.yet.blokblast.domain.model

import kotlinx.serialization.Serializable

/** Absolute or relative integer cell coordinate (x = column, y = row). */
@Serializable
data class Position(val x: Int, val y: Int) {
    operator fun plus(other: Position): Position = Position(x + other.x, y + other.y)
}
