package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.audio.AudioNote
import ge.yet.game.miniapp.audio.AudioMobileBudget
import ge.yet.game.miniapp.audio.CompiledAudioProgram
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.pattern.CycleTime
import ge.yet.game.pattern.PatternEventBuffer
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

/** Fixed-capacity primitive storage reused by the realtime scheduler. */
internal class ScheduledAudioEventBuffer(
    private val capacity: Int = AudioMobileBudget.MAX_TRACKS * PatternQueryBudget.DEFAULT_MAX_EVENTS,
) : AbstractList<ScheduledAudioEvent>() {
    private val trackIndices = IntArray(capacity)
    private val notes = IntArray(capacity)
    private val absoluteStartFrames = LongArray(capacity)
    private val frameOffsets = IntArray(capacity)
    private val durationFrames = LongArray(capacity)
    private val ordersInTrack = IntArray(capacity)

    override var size: Int = 0
        private set

    override fun get(index: Int): ScheduledAudioEvent {
        require(index in 0 until size)
        return ScheduledAudioEvent(
            trackIndex = trackIndices[index],
            note = MidiNote.of(notes[index]),
            absoluteStartFrame = absoluteStartFrames[index],
            frameOffset = frameOffsets[index],
            durationFrames = durationFrames[index],
            orderInTrack = ordersInTrack[index],
        )
    }

    fun clear() {
        size = 0
    }

    fun add(
        trackIndex: Int,
        note: MidiNote,
        absoluteStartFrame: Long,
        frameOffset: Int,
        durationFrames: Long,
        orderInTrack: Int,
    ): Boolean {
        if (size == capacity) return false
        trackIndices[size] = trackIndex
        notes[size] = note.value
        absoluteStartFrames[size] = absoluteStartFrame
        frameOffsets[size] = frameOffset
        this.durationFrames[size] = durationFrames
        ordersInTrack[size] = orderInTrack
        size += 1
        return true
    }

    fun sort() {
        for (index in 1 until size) {
            val trackIndex = trackIndices[index]
            val note = notes[index]
            val startFrame = absoluteStartFrames[index]
            val frameOffset = frameOffsets[index]
            val duration = durationFrames[index]
            val order = ordersInTrack[index]
            var insertion = index
            while (insertion > 0 && comesBefore(startFrame, trackIndex, order, insertion - 1)) {
                copy(from = insertion - 1, to = insertion)
                insertion -= 1
            }
            trackIndices[insertion] = trackIndex
            notes[insertion] = note
            absoluteStartFrames[insertion] = startFrame
            frameOffsets[insertion] = frameOffset
            durationFrames[insertion] = duration
            ordersInTrack[insertion] = order
        }
    }

    fun trackIndexAt(index: Int): Int = trackIndices[index]
    fun noteAt(index: Int): MidiNote = MidiNote.of(notes[index])
    fun frameOffsetAt(index: Int): Int = frameOffsets[index]
    fun durationFramesAt(index: Int): Long = durationFrames[index]

    private fun comesBefore(startFrame: Long, trackIndex: Int, order: Int, other: Int): Boolean =
        startFrame < absoluteStartFrames[other] ||
            (startFrame == absoluteStartFrames[other] && trackIndex < trackIndices[other]) ||
            (startFrame == absoluteStartFrames[other] && trackIndex == trackIndices[other] && order < ordersInTrack[other])

    private fun copy(from: Int, to: Int) {
        trackIndices[to] = trackIndices[from]
        notes[to] = notes[from]
        absoluteStartFrames[to] = absoluteStartFrames[from]
        frameOffsets[to] = frameOffsets[from]
        durationFrames[to] = durationFrames[from]
        ordersInTrack[to] = ordersInTrack[from]
    }
}

internal class AudioScheduler(
    private val program: CompiledAudioProgram,
    private val sampleRate: Int,
) {
    private val tempo = tempoRatio(program.tempo.bpm)
    private val scheduled = ScheduledAudioEventBuffer()
    private val patternEvents = PatternEventBuffer<AudioNote>()
    private val patternBudget = PatternQueryBudget()

    internal val patternEventBufferAllocationCount: Int get() = 1
    internal val patternQueryBudgetAllocationCount: Int get() = 1
    internal val patternListFallbackCount: Int get() = patternEvents.fallbackQueriesUsed

    init {
        require(sampleRate in 8_000..192_000)
    }

    fun scheduleBlock(startFrame: Long, frameCount: Int): List<ScheduledAudioEvent> =
        scheduleBlockInto(startFrame, frameCount).toList()

    fun scheduleBlockInto(startFrame: Long, frameCount: Int): ScheduledAudioEventBuffer {
        require(startFrame >= 0 && frameCount > 0)
        val endFrame = checkedAddPositive(startFrame, frameCount.toLong())
        val scanStartFrame = if (startFrame == 0L) 0L else startFrame - 1L
        val scanArc = TimeArc(frameToCycle(scanStartFrame), frameToCycle(endFrame))
        scheduled.clear()

        for (trackIndex in program.source.musicTracks.indices) {
            val track = program.source.musicTracks[trackIndex]
            var orderInTrack = 0
            var cycle = floorCycle(scanArc.start)
            val lastCycleExclusive = ceilCycle(scanArc.endExclusive)
            while (cycle < lastCycleExclusive) {
                val cycleArc = TimeArc(CycleTime.of(cycle), CycleTime.of(cycle + 1))
                val chunkArc = cycleArc.intersection(scanArc)
                if (chunkArc != null) {
                    patternEvents.clear()
                    patternBudget.reset()
                    track.pattern.queryInto(chunkArc, patternBudget, patternEvents)
                    for (eventIndex in 0 until patternEvents.size) {
                        val wholeStart = patternEvents.wholeStartAt(eventIndex)
                        if (wholeStart !in cycleArc) continue
                        val note = (patternEvents.valueAt(eventIndex) as? AudioNote.Pitched)?.midi ?: continue
                        val absoluteStart = cycleToFrame(wholeStart)
                        if (absoluteStart !in startFrame until endFrame) continue
                        val absoluteEnd = cycleToFrame(patternEvents.wholeEndExclusiveAt(eventIndex))
                        val duration = absoluteEnd - absoluteStart
                        if (duration <= 0) continue
                        scheduled.add(
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

        scheduled.sort()
        return scheduled
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
