package ge.yet.game.pattern

fun <T> euclidean(
    value: T,
    pulses: Int,
    steps: Int,
    rotation: Int = 0,
): Pattern<T> {
    require(steps > 0) { "Euclidean steps must be positive" }
    require(pulses in 0..steps) { "Euclidean pulses must be between zero and steps" }
    val normalizedRotation = floorMod(rotation.toLong(), steps.toLong()).toInt()
    return streamingPattern { arc, budget, output ->
        budget.consumeOperation()
        if (!arc.isEmpty && pulses != 0) {
            forEachOverlappingCycle(arc) { cycle ->
                for (index in 0 until steps) {
                    val rotatedIndex = (index + normalizedRotation) % steps
                    if ((rotatedIndex * pulses) % steps >= pulses) continue
                    val whole = TimeArc(
                        CycleTime.of(cycle) + CycleTime.of(index.toLong(), steps.toLong()),
                        CycleTime.of(cycle) + CycleTime.of(index.toLong() + 1L, steps.toLong()),
                    )
                    whole.intersection(arc)?.let { active ->
                        budget.consumeEvents(1)
                        output.append(whole, active, value)
                    }
                }
            }
        }
    }
}
