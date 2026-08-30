package ge.yet.game.fruitmerge.engine

import kotlin.math.max

enum class ActionRejection {
    GAME_OVER,
    BOARD_BUSY,
    BODY_NOT_FOUND,
    NO_FREE_USE,
    BODY_LIMIT,
    DROP_COOLDOWN,
}

data class ActionResult(
    val state: FruitMergeState,
    val rejection: ActionRejection? = null,
)

data class EngineDiagnostics(
    val maxCandidatePairs: Int = 0,
)

interface FruitMergeRules {
    fun movePreview(state: FruitMergeState, normalizedX: Float): FruitMergeState
    fun drop(state: FruitMergeState): ActionResult
    fun step(state: FruitMergeState, elapsedSeconds: Float): FruitMergeState
    fun beginClear(state: FruitMergeState, paid: Boolean = false): ActionResult
    fun clear(state: FruitMergeState, bodyId: Long, paid: Boolean = false): ActionResult
    fun cancelClear(state: FruitMergeState): FruitMergeState
    fun shake(state: FruitMergeState, paid: Boolean = false): ActionResult
    fun newRun(state: FruitMergeState): FruitMergeState
}

class FruitMergeEngine(
    private val physics: FruitPhysics = FruitPhysics(),
) : FruitMergeRules {
    var diagnostics: EngineDiagnostics = EngineDiagnostics()
        private set

    override fun movePreview(state: FruitMergeState, normalizedX: Float): FruitMergeState {
        if (state.phase != RunPhase.PLAYING || !normalizedX.isFinite()) return state
        val radius = state.previewLevel.radius
        return state.copy(previewX = normalizedX.coerceIn(radius, 1f - radius))
    }

    override fun drop(state: FruitMergeState): ActionResult {
        if (state.phase != RunPhase.PLAYING) return ActionResult(state, ActionRejection.GAME_OVER)
        if (state.dropCooldownSeconds > 0f) return ActionResult(state, ActionRejection.DROP_COOLDOWN)
        if (state.bodies.size >= MAX_BODIES || state.nextBodyId == Long.MAX_VALUE) {
            return ActionResult(state, ActionRejection.BODY_LIMIT)
        }
        val radius = state.previewLevel.radius
        val body = FruitBody(
            id = state.nextBodyId,
            level = state.previewLevel,
            position = Vec2(state.previewX.coerceIn(radius, 1f - radius), SPAWN_Y),
        )
        val nextRandom = state.random.nextInt()
        return ActionResult(
            state.copy(
                bodies = state.bodies + body,
                previewLevel = selectSpawnLevel(nextRandom.value),
                previewX = state.previewX.coerceIn(radius, 1f - radius),
                random = nextRandom.state,
                nextBodyId = state.nextBodyId + 1,
                targetingMode = TargetingMode.NONE,
                dropCooldownSeconds = DROP_COOLDOWN_SECONDS,
            ),
        )
    }

    override fun step(state: FruitMergeState, elapsedSeconds: Float): FruitMergeState {
        if (state.phase != RunPhase.PLAYING) return state
        require(elapsedSeconds.isFinite() && elapsedSeconds in 0f..FruitPhysics.MAX_STEP_SECONDS)
        val physicsResult = physics.step(state.bodies, elapsedSeconds)
        diagnostics = diagnostics.copy(
            maxCandidatePairs = max(diagnostics.maxCandidatePairs, physicsResult.candidatePairCount),
        )
        val merged = resolveMerges(
            state.copy(
                bodies = physicsResult.bodies,
                dropCooldownSeconds = (state.dropCooldownSeconds - elapsedSeconds).coerceAtLeast(0f),
            ),
            physicsResult.contacts,
        )
        val grace = (merged.graceSeconds - elapsedSeconds).coerceAtLeast(0f)
        val overDangerLine = merged.bodies.any { body ->
            body.position.y - body.level.radius < DANGER_Y
        }
        val danger = when {
            grace > 0f -> 0f
            overDangerLine -> merged.dangerSeconds + elapsedSeconds
            else -> 0f
        }
        val phase = if (danger + DANGER_EPSILON >= DANGER_DURATION_SECONDS) {
            RunPhase.RESULT
        } else {
            RunPhase.PLAYING
        }
        return merged.copy(
            bestScore = max(merged.bestScore, merged.score),
            dangerSeconds = danger,
            graceSeconds = grace,
            phase = phase,
            targetingMode = if (phase == RunPhase.RESULT) TargetingMode.NONE else merged.targetingMode,
        )
    }

    override fun beginClear(state: FruitMergeState, paid: Boolean): ActionResult {
        val rejection = actionRejection(state, paid, state.freeClears)
        return if (rejection == null) {
            ActionResult(state.copy(targetingMode = TargetingMode.CLEAR))
        } else {
            ActionResult(state, rejection)
        }
    }

    override fun clear(state: FruitMergeState, bodyId: Long, paid: Boolean): ActionResult {
        val rejection = actionRejection(state, paid, state.freeClears)
        if (rejection != null) return ActionResult(state, rejection)
        if (state.bodies.none { body -> body.id == bodyId }) {
            return ActionResult(state, ActionRejection.BODY_NOT_FOUND)
        }
        return ActionResult(
            state.copy(
                bodies = state.bodies.filterNot { body -> body.id == bodyId },
                freeClears = if (paid) state.freeClears else (state.freeClears - 1).coerceAtLeast(0),
                dangerSeconds = 0f,
                graceSeconds = ACTION_GRACE_SECONDS,
                targetingMode = TargetingMode.NONE,
            ),
        )
    }

    override fun cancelClear(state: FruitMergeState): FruitMergeState =
        state.copy(targetingMode = TargetingMode.NONE)

    override fun shake(state: FruitMergeState, paid: Boolean): ActionResult {
        val rejection = actionRejection(state, paid, state.freeShakes)
        if (rejection != null) return ActionResult(state, rejection)
        if (state.bodies.isEmpty()) {
            return ActionResult(state, ActionRejection.BOARD_BUSY)
        }
        var random = state.random
        val shaken = state.bodies.map { body ->
            val horizontal = random.nextInt().also { random = it.state }.value
            val upward = random.nextInt().also { random = it.state }.value
            val spin = random.nextInt().also { random = it.state }.value
            body.copy(
                velocity = Vec2(
                    x = signedUnit(horizontal) * MAX_SHAKE_HORIZONTAL,
                    y = -(MIN_SHAKE_UPWARD + unit(upward) * (MAX_SHAKE_UPWARD - MIN_SHAKE_UPWARD)),
                ),
                angularVelocity = signedUnit(spin) * MAX_SHAKE_ANGULAR,
                impact = max(body.impact, 0.35f),
            )
        }
        return ActionResult(
            state.copy(
                bodies = shaken,
                random = random,
                freeShakes = if (paid) state.freeShakes else (state.freeShakes - 1).coerceAtLeast(0),
                dangerSeconds = 0f,
                graceSeconds = ACTION_GRACE_SECONDS,
                targetingMode = TargetingMode.NONE,
            ),
        )
    }

    override fun newRun(state: FruitMergeState): FruitMergeState {
        val randomValue = state.random.nextInt()
        return FruitMergeState(
            previewLevel = selectSpawnLevel(randomValue.value),
            random = randomValue.state,
            bestScore = max(state.bestScore, state.score),
            runOrdinal = if (state.runOrdinal == Long.MAX_VALUE) Long.MAX_VALUE else state.runOrdinal + 1,
        )
    }

    private fun resolveMerges(state: FruitMergeState, contacts: List<BodyPair>): FruitMergeState {
        if (contacts.isEmpty()) return state
        val consumedIds = HashSet<Long>(contacts.size * 2)
        val created = ArrayList<FruitBody>(contacts.size)
        var nextBodyId = state.nextBodyId
        var score = state.score
        var mergedAny = false
        val orderedContacts = contacts.sortedWith(
            compareBy<BodyPair> { pair ->
                minOf(state.bodies[pair.firstIndex].id, state.bodies[pair.secondIndex].id)
            }.thenBy { pair ->
                maxOf(state.bodies[pair.firstIndex].id, state.bodies[pair.secondIndex].id)
            },
        )
        for (pair in orderedContacts) {
            val first = state.bodies.getOrNull(pair.firstIndex) ?: continue
            val second = state.bodies.getOrNull(pair.secondIndex) ?: continue
            if (first.level != second.level || first.id in consumedIds || second.id in consumedIds) continue
            consumedIds += first.id
            consumedIds += second.id
            mergedAny = true
            if (first.level == FruitLevel.MELON) {
                score = saturatingAdd(score, FruitLevel.MELON.mergeScore * 2)
                continue
            }
            val nextLevel = first.level.nextOrNull() ?: continue
            if (nextBodyId == Long.MAX_VALUE) continue
            val totalMass = first.level.mass + second.level.mass
            created += FruitBody(
                id = nextBodyId,
                level = nextLevel,
                position = (first.position * first.level.mass + second.position * second.level.mass) / totalMass,
                velocity = ((first.velocity * first.level.mass + second.velocity * second.level.mass) / totalMass)
                    .clampLength(MAX_MERGE_SPEED),
                angle = (first.angle + second.angle) * 0.5f,
                angularVelocity = ((first.angularVelocity + second.angularVelocity) * 0.5f)
                    .coerceIn(-MAX_MERGE_ANGULAR_SPEED, MAX_MERGE_ANGULAR_SPEED),
                impact = 1f,
            )
            nextBodyId += 1
            score = saturatingAdd(score, nextLevel.mergeScore)
        }
        if (!mergedAny) return state
        return state.copy(
            bodies = state.bodies.filterNot { body -> body.id in consumedIds } + created,
            nextBodyId = nextBodyId,
            score = score,
            bestScore = max(state.bestScore, score),
            dangerSeconds = 0f,
            graceSeconds = MERGE_GRACE_SECONDS,
        )
    }

    private fun actionRejection(state: FruitMergeState, paid: Boolean, freeUses: Int): ActionRejection? = when {
        state.phase != RunPhase.PLAYING -> ActionRejection.GAME_OVER
        !paid && freeUses <= 0 -> ActionRejection.NO_FREE_USE
        else -> null
    }

    private fun selectSpawnLevel(value: Int): FruitLevel {
        var cursor = value % FruitLevel.totalSpawnWeight
        for (level in FruitLevel.spawnable) {
            cursor -= level.spawnWeight
            if (cursor < 0) return level
        }
        return FruitLevel.spawnable.last()
    }

    private fun signedUnit(value: Int): Float = unit(value) * 2f - 1f
    private fun unit(value: Int): Float = value.toFloat() / Int.MAX_VALUE.toFloat()

    private fun Vec2.clampLength(maxLength: Float): Vec2 {
        val lengthSquared = lengthSquared()
        return if (lengthSquared > maxLength * maxLength) this * (maxLength / length()) else this
    }

    private fun saturatingAdd(first: Long, second: Long): Long =
        if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second

    companion object {
        const val DANGER_Y: Float = 0.18f
        const val DROP_COOLDOWN_SECONDS: Float = 0.45f

        private const val SPAWN_Y: Float = 0.08f
        private const val DANGER_DURATION_SECONDS: Float = 1.5f
        private const val DANGER_EPSILON: Float = 0.000_1f
        private const val ACTION_GRACE_SECONDS: Float = 0.75f
        private const val MERGE_GRACE_SECONDS: Float = 0.75f
        private const val MAX_SHAKE_HORIZONTAL: Float = 0.55f
        private const val MIN_SHAKE_UPWARD: Float = 0.16f
        private const val MAX_SHAKE_UPWARD: Float = 0.38f
        private const val MAX_SHAKE_ANGULAR: Float = 4f
        private const val MAX_MERGE_SPEED: Float = 2f
        private const val MAX_MERGE_ANGULAR_SPEED: Float = 6f
    }
}
