package ge.yet3.blokblast.utils

import kotlin.math.abs


fun Long.formatScore(): String {
    val isNegative = this < 0
    val absValueStr = abs(this).toString()
    val formatted = absValueStr.reversed().chunked(3).joinToString(",").reversed()
    return if (isNegative) "-$formatted" else formatted
}

fun Int.formatScore(): String = this.toLong().formatScore()