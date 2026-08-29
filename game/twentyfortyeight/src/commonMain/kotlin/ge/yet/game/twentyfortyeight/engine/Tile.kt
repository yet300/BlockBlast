package ge.yet.game.twentyfortyeight.engine

import kotlin.jvm.JvmInline

@JvmInline
internal value class TileValue(val value: Long) {
    init {
        require(value in 1..MAX_VALUE && value and (value - 1L) == 0L) {
            "Tile value must be a positive power of two no greater than $MAX_VALUE: $value"
        }
    }

    companion object {
        const val MAX_VALUE: Long = 1L shl 62
        const val MAX_MERGE_INPUT: Long = 1L shl 61
    }
}

@JvmInline
internal value class TileId(val value: Long) {
    init {
        require(value > 0L) { "Tile ID must be positive: $value" }
    }
}

internal data class RuntimeTile(
    val id: TileId,
    val value: TileValue,
)
