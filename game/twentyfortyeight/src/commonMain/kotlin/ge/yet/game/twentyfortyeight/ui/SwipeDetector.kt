package ge.yet.game.twentyfortyeight.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import ge.yet.game.twentyfortyeight.engine.Direction
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class SwipeConfig(
    val distanceThresholdPx: Float,
    val velocityThresholdPxPerSecond: Float,
    val touchSlopPx: Float,
) {
    init {
        require(distanceThresholdPx.isFinite() && distanceThresholdPx > 0f)
        require(velocityThresholdPxPerSecond.isFinite() && velocityThresholdPxPerSecond > 0f)
        require(touchSlopPx.isFinite() && touchSlopPx >= 0f)
    }
}

internal enum class SwipeStartRegion {
    Gameplay,
    VerticalScrollSupport,
}

internal sealed interface GestureDecision {
    data object PendingTap : GestureDecision
    data object DelegateVerticalScroll : GestureDecision
    data object Cancelled : GestureDecision
    data class GameMove(val direction: Direction) : GestureDecision
}

internal fun resolveGesture(
    delta: Offset,
    velocity: Velocity,
    cancelled: Boolean,
    enabled: Boolean,
    startRegion: SwipeStartRegion,
    config: SwipeConfig,
): GestureDecision {
    if (cancelled || !enabled) return GestureDecision.Cancelled

    val distance = max(abs(delta.x), abs(delta.y))
    val crossedTouchSlop = distance >= config.touchSlopPx
    val crossedDistance = distance >= config.distanceThresholdPx
    val crossedVelocity = max(abs(velocity.x), abs(velocity.y)) >=
        config.velocityThresholdPxPerSecond
    val locked = crossedDistance || (crossedTouchSlop && crossedVelocity)
    if (!locked) return GestureDecision.PendingTap
    val resolved = if (crossedDistance) delta else Offset(velocity.x, velocity.y)
    val horizontal = abs(resolved.x) >= abs(resolved.y)
    if (!horizontal && startRegion == SwipeStartRegion.VerticalScrollSupport) {
        return GestureDecision.DelegateVerticalScroll
    }
    return GestureDecision.GameMove(
        when {
            horizontal && resolved.x >= 0f -> Direction.Right
            horizontal -> Direction.Left
            resolved.y >= 0f -> Direction.Down
            else -> Direction.Up
        },
    )
}

internal fun Modifier.detectTwentyFortyEightSwipes(
    enabled: Boolean,
    supportBoundsInViewport: Rect?,
    onDirection: (Direction) -> Unit,
): Modifier = composed {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentSupportBounds by rememberUpdatedState(supportBoundsInViewport)
    val currentOnDirection by rememberUpdatedState(onDirection)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val density = LocalDensity.current
    val maximumDistance = with(density) { 48.dp.toPx() }
    val velocityThreshold = with(density) { 650.dp.toPx() }

    pointerInput(touchSlop, maximumDistance, velocityThreshold) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            if (!currentEnabled) return@awaitEachGesture

            val startRegion = if (currentSupportBounds?.contains(down.position) == true) {
                SwipeStartRegion.VerticalScrollSupport
            } else {
                SwipeStartRegion.Gameplay
            }
            val config = SwipeConfig(
                distanceThresholdPx = min(
                    max(touchSlop, min(size.width, size.height) * DISTANCE_FRACTION),
                    maximumDistance,
                ),
                velocityThresholdPxPerSecond = velocityThreshold,
                touchSlopPx = touchSlop,
            )
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                tracker.addPosition(change.uptimeMillis, change.position)

                when (
                    val decision = resolveGesture(
                        delta = change.position - down.position,
                        velocity = tracker.calculateVelocity(),
                        cancelled = change.isConsumed,
                        enabled = currentEnabled,
                        startRegion = startRegion,
                        config = config,
                    )
                ) {
                    is GestureDecision.GameMove -> {
                        currentOnDirection(decision.direction)
                        change.consume()
                        if (!change.pressed) return@awaitEachGesture
                        do {
                            val remaining = awaitPointerEvent(PointerEventPass.Initial)
                            remaining.changes.forEach { pointer -> pointer.consume() }
                        } while (remaining.changes.any { it.pressed })
                        return@awaitEachGesture
                    }
                    GestureDecision.DelegateVerticalScroll,
                    GestureDecision.Cancelled,
                    -> return@awaitEachGesture
                    GestureDecision.PendingTap -> if (!change.pressed) return@awaitEachGesture
                }
            }
        }
    }
}

private const val DISTANCE_FRACTION = 0.08f
