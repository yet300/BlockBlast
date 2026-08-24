package ge.yet.game.pattern

/** Exact cycle-relative time represented as a normalized rational number. */
@ConsistentCopyVisibility
data class CycleTime private constructor(
    val numerator: Long,
    val denominator: Long,
) : Comparable<CycleTime> {

    init {
        require(denominator > 0) { "A normalized cycle-time denominator must be positive" }
    }

    operator fun plus(other: CycleTime): CycleTime {
        val commonDivisor = greatestCommonDivisor(denominator, other.denominator)
        val leftFactor = other.denominator / commonDivisor
        val rightFactor = denominator / commonDivisor
        return of(
            checkedAdd(
                checkedMultiply(numerator, leftFactor),
                checkedMultiply(other.numerator, rightFactor),
            ),
            checkedMultiply(denominator, leftFactor),
        )
    }

    operator fun minus(other: CycleTime): CycleTime = this + -other

    operator fun times(other: CycleTime): CycleTime {
        val leftDivisor = greatestCommonDivisorMagnitude(numerator, other.denominator)
        val rightDivisor = greatestCommonDivisorMagnitude(other.numerator, denominator)
        return of(
            checkedMultiply(
                divideByMagnitude(numerator, leftDivisor),
                divideByMagnitude(other.numerator, rightDivisor),
            ),
            checkedMultiply(
                divideByMagnitude(denominator, rightDivisor),
                divideByMagnitude(other.denominator, leftDivisor),
            ),
        )
    }

    operator fun div(other: CycleTime): CycleTime {
        if (other.numerator == 0L) throw ArithmeticException("Cannot divide cycle time by zero")
        val numeratorDivisor = greatestCommonDivisorMagnitude(numerator, other.numerator)
        val denominatorDivisor = greatestCommonDivisorMagnitude(other.denominator, denominator)
        return of(
            checkedMultiply(
                divideByMagnitude(numerator, numeratorDivisor),
                divideByMagnitude(other.denominator, denominatorDivisor),
            ),
            checkedMultiply(
                divideByMagnitude(denominator, denominatorDivisor),
                divideByMagnitude(other.numerator, numeratorDivisor),
            ),
        )
    }

    operator fun unaryMinus(): CycleTime = of(checkedNegate(numerator), denominator)

    override fun compareTo(other: CycleTime): Int = compareFractions(
        leftNumerator = numerator,
        leftDenominator = denominator,
        rightNumerator = other.numerator,
        rightDenominator = other.denominator,
    )

    companion object {
        val ZERO: CycleTime = CycleTime(0, 1)
        val ONE: CycleTime = CycleTime(1, 1)

        fun of(numerator: Long, denominator: Long = 1): CycleTime {
            require(denominator != 0L) { "A cycle-time denominator must not be zero" }
            if (numerator == 0L) return ZERO

            val divisor = greatestCommonDivisorMagnitude(numerator, denominator)
            var normalizedNumerator = divideByMagnitude(numerator, divisor)
            var normalizedDenominator = divideByMagnitude(denominator, divisor)
            if (normalizedDenominator < 0L) {
                normalizedNumerator = checkedNegate(normalizedNumerator)
                normalizedDenominator = checkedNegate(normalizedDenominator)
            }
            return CycleTime(normalizedNumerator, normalizedDenominator)
        }
    }
}

private fun compareFractions(
    leftNumerator: Long,
    leftDenominator: Long,
    rightNumerator: Long,
    rightDenominator: Long,
): Int {
    var leftN = leftNumerator
    var leftD = leftDenominator
    var rightN = rightNumerator
    var rightD = rightDenominator
    var reversed = false

    while (true) {
        val leftQuotient = floorDivide(leftN, leftD)
        val rightQuotient = floorDivide(rightN, rightD)
        if (leftQuotient != rightQuotient) {
            return if (reversed) {
                rightQuotient.compareTo(leftQuotient)
            } else {
                leftQuotient.compareTo(rightQuotient)
            }
        }

        val leftRemainder = floorRemainder(leftN, leftD)
        val rightRemainder = floorRemainder(rightN, rightD)
        if (leftRemainder == 0L || rightRemainder == 0L) {
            if (leftRemainder == rightRemainder) return 0
            val result = if (leftRemainder == 0L) -1 else 1
            return if (reversed) -result else result
        }

        leftN = leftD
        leftD = leftRemainder
        rightN = rightD
        rightD = rightRemainder
        reversed = !reversed
    }
}

private fun floorDivide(value: Long, positiveDivisor: Long): Long {
    val quotient = value / positiveDivisor
    val remainder = value % positiveDivisor
    return if (remainder < 0L) quotient - 1L else quotient
}

private fun floorRemainder(value: Long, positiveDivisor: Long): Long {
    val remainder = value % positiveDivisor
    return if (remainder < 0L) remainder + positiveDivisor else remainder
}

private fun greatestCommonDivisor(left: Long, right: Long): Long =
    greatestCommonDivisorMagnitude(left, right).toLong()

private fun greatestCommonDivisorMagnitude(left: Long, right: Long): ULong {
    var a = magnitude(left)
    var b = magnitude(right)
    while (b != 0uL) {
        val remainder = a % b
        a = b
        b = remainder
    }
    return a
}

private fun magnitude(value: Long): ULong =
    if (value >= 0L) value.toULong() else 0uL - value.toULong()

private fun divideByMagnitude(value: Long, divisor: ULong): Long {
    require(divisor != 0uL)
    return if (divisor == Long.MIN_VALUE.toULong()) {
        check(value == Long.MIN_VALUE)
        -1L
    } else {
        value / divisor.toLong()
    }
}

private fun checkedAdd(left: Long, right: Long): Long {
    val result = left + right
    if (((left xor result) and (right xor result)) < 0L) {
        throw ArithmeticException("Cycle-time addition overflow")
    }
    return result
}

private fun checkedMultiply(left: Long, right: Long): Long {
    if (left == 0L || right == 0L) return 0L
    if ((left == Long.MIN_VALUE && right == -1L) || (right == Long.MIN_VALUE && left == -1L)) {
        throw ArithmeticException("Cycle-time multiplication overflow")
    }
    val result = left * right
    if (result / right != left) throw ArithmeticException("Cycle-time multiplication overflow")
    return result
}

private fun checkedNegate(value: Long): Long {
    if (value == Long.MIN_VALUE) throw ArithmeticException("Cycle-time negation overflow")
    return -value
}
