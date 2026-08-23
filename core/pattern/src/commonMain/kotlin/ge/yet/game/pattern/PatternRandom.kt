package ge.yet.game.pattern

fun <T> choose(seed: Long, values: List<T>): Pattern<T> {
    require(values.isNotEmpty()) { "A choose pattern requires at least one value" }
    val snapshot = values.toList()
    return pattern { arc, budget ->
        budget.consumeOperation()
        if (arc.isEmpty) {
            emptyList()
        } else {
            val events = mutableListOf<PatternEvent<T>>()
            forEachOverlappingCycle(arc) { cycle ->
                val whole = TimeArc(CycleTime.of(cycle), CycleTime.of(cycle + 1L))
                whole.intersection(arc)?.let { active ->
                    val index = deterministicIndex(seed, cycle.toULong(), snapshot.size)
                    budget.consumeEvents(1)
                    events += PatternEvent(whole, active, snapshot[index])
                }
            }
            events
        }
    }
}

fun <T> Pattern<T>.degrade(probability: Float, seed: Long): Pattern<T> {
    require(probability.isFinite() && probability in 0f..1f) {
        "A degrade probability must be finite and in 0..1"
    }
    return pattern { arc, budget ->
        budget.consumeOperation()
        query(arc, budget).filter { event ->
            deterministicUnit(seed, event.stableTimeKey()) >= probability.toDouble()
        }
    }
}

private fun PatternEvent<*>.stableTimeKey(): ULong {
    var key = whole.start.numerator.toULong()
    key = mix64(key xor whole.start.denominator.toULong())
    key = mix64(key xor whole.endExclusive.numerator.toULong())
    return mix64(key xor whole.endExclusive.denominator.toULong())
}

private fun deterministicIndex(seed: Long, key: ULong, size: Int): Int =
    (mix64(seed.toULong() xor key) % size.toULong()).toInt()

private fun deterministicUnit(seed: Long, key: ULong): Double {
    val bits = mix64(seed.toULong() xor key) shr 11
    return bits.toDouble() / (1uL shl 53).toDouble()
}

private fun mix64(input: ULong): ULong {
    var value = input + 0x9E3779B97F4A7C15uL
    value = (value xor (value shr 30)) * 0xBF58476D1CE4E5B9uL
    value = (value xor (value shr 27)) * 0x94D049BB133111EBuL
    return value xor (value shr 31)
}
