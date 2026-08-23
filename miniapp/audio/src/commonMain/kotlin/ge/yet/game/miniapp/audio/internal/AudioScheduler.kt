package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.audio.AudioNote
import ge.yet.game.miniapp.audio.CompiledAudioProgram
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.pattern.CycleTime
import ge.yet.game.pattern.PatternQueryBudget
import ge.yet.game.pattern.TimeArc
import kotlin.math.roundToLong

internal data class ScheduledAudioEvent(
    val trackIndex: Int,
    val note: MidiNote,
    val absoluteStartFrame: Long,
    val frameOffset: Int,
    val durationFrames: Long,
    internal val orderInTrack: Int,
)

internal class AudioScheduler(
    private val program: CompiledAudioProgram,
    private val sampleRate: Int,
) {
    private val tempo = tempoRatio(program.tempo.bpm)

    init {
        require(sampleRate in 8_000..192_000)
    }

    fun scheduleBlock(startFrame: Long, frameCount: Int): List<ScheduledAudioEvent> {
        require(startFrame >= 0 && frameCount > 0)
        val endFrame = checkedAddPositive(startFrame, frameCount.toLong())
        val scanStartFrame = if (startFrame == 0L) 0L else startFrame - 1L
        val scanArc = TimeArc(frameToCycle(scanStartFrame), frameToCycle(endFrame))
        val scheduled = mutableListOf<ScheduledAudioEvent>()

        program.source.musicTracks.forEachIndexed { trackIndex, track ->
            var orderInTrack = 0
            var cycle = floorCycle(scanArc.start)
            val lastCycleExclusive = ceilCycle(scanArc.endExclusive)
            while (cycle < lastCycleExclusive) {
                val cycleArc = TimeArc(CycleTime.of(cycle), CycleTime.of(cycle + 1))
                val chunkArc = cycleArc.intersection(scanArc)
                if (chunkArc != null) {
                    track.pattern.query(chunkArc, PatternQueryBudget()).forEach { event ->
                        if (event.whole.start !in cycleArc) return@forEach
                        val note = (event.value as? AudioNote.Pitched)?.midi ?: return@forEach
                        val absoluteStart = cycleToFrame(event.whole.start)
                        if (absoluteStart !in startFrame until endFrame) return@forEach
                        val absoluteEnd = cycleToFrame(event.whole.endExclusive)
                        val duration = absoluteEnd - absoluteStart
                        if (duration <= 0) return@forEach
                        scheduled += ScheduledAudioEvent(
                            trackIndex = trackIndex,
                            note = note,
                            absoluteStartFrame = absoluteStart,
                            frameOffset = (absoluteStart - startFrame).toInt(),
                            durationFrames = duration,
                            orderInTrack = orderInTrack++,
                        )
                    }
                }
                cycle += 1
            }
        }

        return scheduled.sortedWith(
            compareBy<ScheduledAudioEvent>(ScheduledAudioEvent::absoluteStartFrame)
                .thenBy(ScheduledAudioEvent::trackIndex)
                .thenBy(ScheduledAudioEvent::orderInTrack),
        )
    }

    private fun frameToCycle(frame: Long): CycleTime = CycleTime.of(
        numerator = checkedMultiplyPositive(frame, tempo.numerator),
        denominator = checkedMultiplyPositive(sampleRate.toLong() * BEATS_PER_CYCLE, tempo.denominator),
    )

    private fun cycleToFrame(time: CycleTime): Long = (
        time.numerator.toDouble() * sampleRate * BEATS_PER_CYCLE * tempo.denominator /
            (time.denominator.toDouble() * tempo.numerator)
        ).roundToLong()
}

private data class TempoRatio(val numerator: Long, val denominator: Long)

private fun tempoRatio(bpm: Float): TempoRatio {
    val scaled = (bpm * TEMPO_PRECISION).roundToLong()
    val divisor = greatestCommonDivisor(scaled, TEMPO_PRECISION)
    return TempoRatio(scaled / divisor, TEMPO_PRECISION / divisor)
}

private fun greatestCommonDivisor(first: Long, second: Long): Long {
    var left = first
    var right = second
    while (right != 0L) {
        val remainder = left % right
        left = right
        right = remainder
    }
    return left
}

private fun floorCycle(time: CycleTime): Long = time.numerator / time.denominator

private fun ceilCycle(time: CycleTime): Long =
    time.numerator / time.denominator + if (time.numerator % time.denominator == 0L) 0 else 1

private fun checkedAddPositive(left: Long, right: Long): Long {
    require(left >= 0 && right >= 0 && left <= Long.MAX_VALUE - right) { "Audio frame range overflow" }
    return left + right
}

private fun checkedMultiplyPositive(left: Long, right: Long): Long {
    require(left >= 0 && right > 0 && (left == 0L || left <= Long.MAX_VALUE / right)) {
        "Audio time conversion overflow"
    }
    return left * right
}

private const val TEMPO_PRECISION = 1_000_000L
private const val BEATS_PER_CYCLE = 240L
