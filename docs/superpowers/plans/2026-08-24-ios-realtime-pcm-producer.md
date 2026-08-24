# iOS Realtime PCM Producer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move all iOS command dispatch, pattern scheduling and DSP rendering off the `AVAudioSourceNode` callback onto a session-owned PCM producer while preserving lifecycle, policy and diagnostic behavior.

**Architecture:** A fixed common SPSC stereo ring separates one iOS producer thread from one native callback reader. The producer exclusively owns `CompiledAudioRuntime` and `RealtimeAudioRenderer`; `IosAudioSinkSession` coordinates prefill, engine lifecycle and stale transition rejection, while the callback only drains fixed PCM storage and updates atomic counters.

**Tech Stack:** Kotlin 2.4.10, Kotlin Multiplatform, Kotlin common atomics, Kotlin/Native Foundation (`NSThread`, `NSCondition`), AVFAudio (`AVAudioEngine`, `AVAudioSourceNode`), kotlin-test, Gradle KMP.

---

## Execution Constraints

- Work only on the existing `codex/miniapp-plugin-framework` branch. The user explicitly rejected worktrees.
- Before implementation, read `.agents/skills/miniapp-procedural-audio/SKILL.md`, `superpowers:test-driven-development` and `superpowers:executing-plans` completely.
- Preserve the public `MiniAppAudio`/`AudioProgram` authoring surface and the Android sink.
- Treat `docs/superpowers/specs/2026-08-24-ios-realtime-pcm-producer-design.md` as authoritative.
- Use `apply_patch` for source edits and commit after each green task.

## File Map

### Create

- `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/StereoPcmRingBuffer.kt` — fixed SPSC stereo PCM storage and atomic frame publication.
- `miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/internal/StereoPcmRingBufferTest.kt` — portable ordering, wrap, capacity, underrun and reuse proofs.
- `miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal/IosPcmCallbackSource.kt` — callback-only ring drain, in-flight barrier and atomic diagnostic deltas.
- `miniapp/audio/src/iosTest/kotlin/ge/yet/game/miniapp/audio/internal/IosPcmCallbackSourceTest.kt` — callback PCM/underrun/quiescence proofs.
- `miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal/IosPcmProducer.kt` — buffer policy, exclusive runtime/renderer ownership, producer state and bounded block generation.
- `miniapp/audio/src/iosTest/kotlin/ge/yet/game/miniapp/audio/internal/IosPcmProducerTest.kt` — deterministic producer behavior and ownership tests.
- `miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal/IosProducerThread.kt` — `NSThread`/`NSCondition` worker boundary.
- `miniapp/audio/src/iosTest/kotlin/ge/yet/game/miniapp/audio/internal/IosProducerThreadTest.kt` — real Foundation thread start/wake/termination proof.

### Modify

- `miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal/IosAudioSink.kt` — replace callback rendering with producer-backed PCM, split locked decisions from blocking lifecycle work and harden native pointer handling.
- `miniapp/audio/src/iosTest/kotlin/ge/yet/game/miniapp/audio/internal/IosAudioSinkTest.kt` — update fakes and verify prefill/start, pause/reset, interruption, route reset, media reset and release ordering.
- `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/CompiledAudioRuntime.kt` — add an internal producer-only consume path that propagates target failures.
- `miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/internal/CompiledAudioRuntimeTest.kt` — prove legacy containment and producer propagation semantics.
- `docs/superpowers/plans/2026-08-23-miniapp-procedural-audio.md` — link this sub-plan from Task 15 and mark the iOS callback audit as delegated here.
- `AGENTS.md` — describe the iOS producer/ring boundary in the `:miniapp:audio` module row and procedural-audio architecture guidance.

## Task 1: Add the fixed SPSC stereo PCM ring

**Files:**

- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/StereoPcmRingBuffer.kt`
- Create: `miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/internal/StereoPcmRingBufferTest.kt`

- [x] **Step 1: Write RED tests for construction and contiguous transfer**

Create tests that establish the primitive contract:

```kotlin
class StereoPcmRingBufferTest {
    @Test
    fun `capacity must be a positive power of two`() {
        assertFailsWith<IllegalArgumentException> { StereoPcmRingBuffer(0) }
        assertFailsWith<IllegalArgumentException> { StereoPcmRingBuffer(6) }
        assertEquals(8, StereoPcmRingBuffer(8).capacityFrames)
    }

    @Test
    fun `write and read preserve stereo frame order`() {
        val ring = StereoPcmRingBuffer(8)
        val left = floatArrayOf(1f, 2f, 3f)
        val right = floatArrayOf(-1f, -2f, -3f)
        val outputLeft = FloatArray(3)
        val outputRight = FloatArray(3)

        assertEquals(3, ring.write(left, right, 3))
        assertEquals(0, ring.readOrSilence(outputLeft, outputRight, 3))
        assertContentEquals(left, outputLeft)
        assertContentEquals(right, outputRight)
        assertEquals(0, ring.availableFrames)
    }
}
```

- [x] **Step 2: Run the test and verify RED**

Run:

```bash
./gradlew :miniapp:audio:testAndroidHostTest --tests '*StereoPcmRingBufferTest*'
```

Expected: Kotlin compilation fails because `StereoPcmRingBuffer` does not exist.

- [x] **Step 3: Implement construction and contiguous transfer**

Add an internal class with this exact surface:

```kotlin
@OptIn(ExperimentalAtomicApi::class)
internal class StereoPcmRingBuffer(
    val capacityFrames: Int,
) {
    private val mask: Int
    private val leftSamples: FloatArray
    private val rightSamples: FloatArray
    private val readPosition = AtomicLong(0)
    private val writePosition = AtomicLong(0)

    val availableFrames: Int
        get() = (writePosition.load() - readPosition.load()).toInt()

    val freeFrames: Int
        get() = capacityFrames - availableFrames

    init {
        require(capacityFrames > 0 && capacityFrames and (capacityFrames - 1) == 0)
        mask = capacityFrames - 1
        leftSamples = FloatArray(capacityFrames)
        rightSamples = FloatArray(capacityFrames)
    }

    fun write(left: FloatArray, right: FloatArray, frameCount: Int): Int {
        val currentWrite = writePosition.load()
        val writable = minOf(frameCount, capacityFrames - (currentWrite - readPosition.load()).toInt())
        val start = (currentWrite and mask.toLong()).toInt()
        val first = minOf(writable, capacityFrames - start)
        left.copyInto(leftSamples, start, 0, first)
        right.copyInto(rightSamples, start, 0, first)
        val second = writable - first
        if (second != 0) {
            left.copyInto(leftSamples, 0, first, writable)
            right.copyInto(rightSamples, 0, first, writable)
        }
        writePosition.store(currentWrite + writable)
        return writable
    }

    fun readOrSilence(left: FloatArray, right: FloatArray, frameCount: Int): Int {
        val currentRead = readPosition.load()
        val readable = minOf(frameCount, (writePosition.load() - currentRead).toInt())
        val start = (currentRead and mask.toLong()).toInt()
        val first = minOf(readable, capacityFrames - start)
        leftSamples.copyInto(left, 0, start, start + first)
        rightSamples.copyInto(right, 0, start, start + first)
        val second = readable - first
        if (second != 0) {
            leftSamples.copyInto(left, first, 0, second)
            rightSamples.copyInto(right, first, 0, second)
        }
        left.fill(0f, readable, frameCount)
        right.fill(0f, readable, frameCount)
        readPosition.store(currentRead + readable)
        return frameCount - readable
    }

    fun reset() {
        readPosition.store(0)
        writePosition.store(0)
    }
}
```

Construction validates capacity. Callers use preallocated arrays sized from the
same producer configuration, so the hot read/write methods contain no
`require`, exception construction or result allocation. `write` returns written
frames. `readOrSilence` returns missing frames, not a result object. Publish the
write position only after both channels have been copied and the read position
only after the output has been filled.

- [x] **Step 4: Add RED tests for wrap, full buffer and underrun silence**

Add cases that:

- write six/read four/write six/read eight through the physical wrap boundary;
- fill all eight frames and prove the ninth frame is not written;
- request five frames with only two available and assert the final three frames
  in both output arrays are zero;
- call `reset`, reuse the same instance and verify positions return to zero.

Use distinct values per channel and assert complete arrays with
`assertContentEquals`; do not inspect private arrays or use reflection.

- [x] **Step 5: Run the ring tests and verify GREEN**

Run:

```bash
./gradlew :miniapp:audio:testAndroidHostTest --tests '*StereoPcmRingBufferTest*'
```

Expected: all `StereoPcmRingBufferTest` cases pass on configured common targets.

- [x] **Step 6: Commit the ring**

```bash
git add miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/StereoPcmRingBuffer.kt miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/internal/StereoPcmRingBufferTest.kt
git commit -m "feat: add fixed stereo PCM ring"
```

## Task 2: Add the callback-only PCM source and diagnostics

**Files:**

- Create: `miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal/IosPcmCallbackSource.kt`
- Create: `miniapp/audio/src/iosTest/kotlin/ge/yet/game/miniapp/audio/internal/IosPcmCallbackSourceTest.kt`

- [x] **Step 1: Write RED tests for prepared PCM and partial underrun**

Use a ring of eight frames, write known stereo samples, then invoke the source
twice. Assert that the first call returns prepared PCM and the second call
returns its available prefix followed by zero. Assert the drained diagnostics:

```kotlin
assertEquals(
    IosPcmCallbackDiagnostics(
        renderedFrames = 5,
        underrunFrames = 3,
        underrunEvents = 1,
        callbackFailures = 0,
    ),
    source.drainDiagnostics(),
)
```

Invoke `source.render` directly; do not involve the runtime or renderer in this
test.

- [x] **Step 2: Run the iOS test and verify RED**

Run:

```bash
./gradlew :miniapp:audio:iosSimulatorArm64Test --tests '*IosPcmCallbackSourceTest*'
```

Expected: compilation fails because the callback source and diagnostics do not exist.

- [x] **Step 3: Implement the callback source**

Keep the callback surface primitive:

```kotlin
internal data class IosPcmCallbackDiagnostics(
    val renderedFrames: Long,
    val underrunFrames: Long,
    val underrunEvents: Long,
    val callbackFailures: Long,
)

@OptIn(ExperimentalAtomicApi::class)
internal class IosPcmCallbackSource(
    private val ring: StereoPcmRingBuffer,
) : IosPcmBlockRenderer {
    private val callbacksInFlight = AtomicLong(0)
    private val renderedFrames = AtomicLong(0)
    private val underrunFrames = AtomicLong(0)
    private val underrunEvents = AtomicLong(0)
    private val callbackFailures = AtomicLong(0)

    override fun render(left: FloatArray, right: FloatArray, frameCount: Int) {
        callbacksInFlight.fetchAndAdd(1)
        try {
            val missing = ring.readOrSilence(left, right, frameCount)
            renderedFrames.fetchAndAdd((frameCount - missing).toLong())
            if (missing != 0) {
                underrunFrames.fetchAndAdd(missing.toLong())
                underrunEvents.fetchAndAdd(1)
            }
        } finally {
            callbacksInFlight.fetchAndAdd(-1)
        }
    }

    override fun recordCallbackFailure() {
        callbackFailures.fetchAndAdd(1)
    }

    fun hasCallbackInFlight(): Boolean = callbacksInFlight.load() != 0L
    fun drainDiagnostics(): IosPcmCallbackDiagnostics
}
```

`drainDiagnostics` uses `exchange(0)` for each diagnostic counter. It must not
reset `callbacksInFlight`.

- [x] **Step 4: Add barrier and reuse tests**

Add a test-only ring/source sequence proving:

- repeated `render` calls reuse the source and output buffers;
- `recordCallbackFailure` is aggregated and drained exactly once;
- `hasCallbackInFlight()` is false after normal and zero-frame renders, proving
  the callback closes its in-flight barrier on every valid exit.

- [x] **Step 5: Run iOS tests and verify GREEN**

Run:

```bash
./gradlew :miniapp:audio:iosSimulatorArm64Test --tests '*IosPcmCallbackSourceTest*'
```

Expected: all callback-source tests pass.

- [x] **Step 6: Commit the callback source**

```bash
git add miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal/IosPcmCallbackSource.kt miniapp/audio/src/iosTest/kotlin/ge/yet/game/miniapp/audio/internal/IosPcmCallbackSourceTest.kt
git commit -m "feat: add allocation-free iOS PCM callback source"
```

## Task 3: Implement deterministic producer ownership and watermarks

**Files:**

- Create: `miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal/IosPcmProducer.kt`
- Create: `miniapp/audio/src/iosTest/kotlin/ge/yet/game/miniapp/audio/internal/IosPcmProducerTest.kt`
- Modify: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/CompiledAudioRuntime.kt`
- Modify: `miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/internal/CompiledAudioRuntimeTest.kt`

- [x] **Step 1: RED-test the buffer policy independently**

Define expected selections:

```kotlin
assertEquals(
    IosPcmBufferConfiguration(
        producerQuantum = 256,
        ringCapacity = 2_048,
        startWatermark = 768,
        targetWatermark = 1_536,
    ),
    IosPcmBufferConfiguration.select(maximumFramesPerSlice = 256),
)
assertEquals(
    IosPcmBufferConfiguration(
        producerQuantum = 512,
        ringCapacity = 4_096,
        startWatermark = 4_096,
        targetWatermark = 4_096,
    ),
    IosPcmBufferConfiguration.select(maximumFramesPerSlice = 4_096),
)
```

- [x] **Step 2: Add RED producer tests with a recording renderer**

Use a deterministic `pumpOnce()` seam and assert:

- `resume` plus repeated pumps reaches but never exceeds `targetWatermark`;
- the first pump consumes an accepted `PlayMusic` before rendering;
- `updatePolicy` is applied by `pumpOnce`, not by the calling test thread;
- a full ring prevents another renderer call;
- `pauseAndReset` clears the ring only after producer work is quiescent;
- a renderer exception records one failure, enters terminal failure and never
  publishes the partially rendered block;
- `terminate` calls `renderer.destroy()` once and rejects later commands.

The recording renderer fills complete blocks with a monotonically increasing
sample value so publication boundaries are observable.

- [x] **Step 3: Run producer tests and verify RED**

Run:

```bash
./gradlew :miniapp:audio:iosSimulatorArm64Test --tests '*IosPcmProducerTest*'
```

Expected: compilation fails because producer types do not exist.

- [x] **Step 4: Implement producer contracts and buffer policy**

Use these internal types:

```kotlin
internal data class IosPcmBufferConfiguration(
    val producerQuantum: Int,
    val ringCapacity: Int,
    val startWatermark: Int,
    val targetWatermark: Int,
) {
    companion object {
        fun select(maximumFramesPerSlice: Int): IosPcmBufferConfiguration {
            require(maximumFramesPerSlice in 1..MAXIMUM_SAFE_RING_FRAMES)
            val quantum = maximumFramesPerSlice.coerceIn(64, 512)
            val minimumCapacity = maxOf(8 * quantum, maximumFramesPerSlice)
            var capacity = 1
            while (capacity < minimumCapacity) capacity = capacity shl 1
            return IosPcmBufferConfiguration(
                producerQuantum = quantum,
                ringCapacity = capacity,
                startWatermark = minOf(capacity, maxOf(3 * quantum, maximumFramesPerSlice)),
                targetWatermark = minOf(capacity, maxOf(6 * quantum, maximumFramesPerSlice)),
            )
        }
    }
}

internal data class IosPcmProducerDiagnostics(
    val producerWakeups: Long,
    val renderFailures: Long,
    val peakBufferedFrames: Long,
)

internal interface IosPcmProducerSession {
    val callbackSource: IosPcmCallbackSource
    fun submit(command: AudioCommand): AudioRuntimeSubmitResult
    fun updatePolicy(policy: AudioSessionPolicy)
    fun resumeAndAwaitPrefill(): Boolean
    fun pauseAndReset()
    fun terminate()
    fun drainRuntimeDiagnostics(): AudioRuntimeDiagnosticsSnapshot
    fun drainProducerDiagnostics(): IosPcmProducerDiagnostics
}

internal fun interface IosPcmProducerFactory {
    fun create(
        sampleRate: Int,
        maximumFramesPerSlice: Int,
        rendererFactory: IosAudioRendererFactory,
    ): IosPcmProducerSession
}
```

The shown integer-shift loop is the complete `nextPowerOfTwo` operation. Define
`MAXIMUM_SAFE_RING_FRAMES = 1 shl 29`, so it cannot overflow `Int` before
allocation.

- [x] **Step 5: RED-test producer-only command failure propagation**

In `CompiledAudioRuntimeTest`, use a target whose `playMusic` throws. Assert the
existing `consumeCommandsForBlock()` still contains the failure and increments
`callbackFailures`, while a new `consumeCommandsForBlockOrThrow()` propagates
the same exception. This preserves Android behavior while allowing the iOS
producer to enter its terminal failure state.

- [x] **Step 6: Implement producer-only command failure propagation**

Refactor the current consume loop behind a private boolean policy:

```kotlin
fun consumeCommandsForBlock(): Int = consumeCommandsForBlock(propagateTargetFailure = false)

fun consumeCommandsForBlockOrThrow(): Int = consumeCommandsForBlock(propagateTargetFailure = true)

private fun consumeCommandsForBlock(propagateTargetFailure: Boolean): Int {
    if (isDestroyed) return 0
    var consumed = 0
    while (consumed < maxCommandsPerBlock) {
        val command = queue.poll() ?: break
        consumed += 1
        val outcome = if (propagateTargetFailure) {
            dispatch(command)
        } else {
            try {
                dispatch(command)
            } catch (_: Throwable) {
                diagnostics.increment(AudioRuntimeDiagnostic.CALLBACK_FAILURE)
                null
            }
        }
        if (outcome != null) diagnostics.record(outcome)
        if (command === AudioCommand.Destroy) {
            isDestroyed = true
            queue.clear()
            break
        }
    }
    return consumed
}
```

- [x] **Step 7: Implement the deterministic producer core**

The concrete producer owns:

```kotlin
private val configuration = IosPcmBufferConfiguration.select(maximumFramesPerSlice)
private val ring = StereoPcmRingBuffer(configuration.ringCapacity)
override val callbackSource = IosPcmCallbackSource(ring)
private val renderer = rendererFactory.create(sampleRate, configuration.producerQuantum)
private val runtime = CompiledAudioRuntime(renderer, COMMAND_QUEUE_CAPACITY, MAX_COMMANDS_PER_BLOCK)
private val left = FloatArray(configuration.producerQuantum)
private val right = FloatArray(configuration.producerQuantum)
```

Protect `runtime.submit`, command consumption and diagnostic drain with one
producer command lock. `pumpOnce()` must:

1. return without work unless state is running and below target;
2. call `runtime.consumeCommandsForBlockOrThrow()` under the command lock;
3. apply a changed desired policy on the producer side;
4. compute `frames = minOf(producerQuantum, ring.freeFrames)`;
5. render into the reusable arrays;
6. publish exactly `frames` only after successful render;
7. update peak buffered frames without allocating.

Catch failures around command consumption/render in the producer boundary,
clear the work arrays, record one producer failure and enter terminal failure.

- [x] **Step 8: Run producer tests and the complete iOS test target**

Run:

```bash
./gradlew :miniapp:audio:iosSimulatorArm64Test
```

Expected: all existing and new iOS tests pass.

- [x] **Step 9: Commit deterministic producer behavior**

```bash
git add miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal/IosPcmProducer.kt miniapp/audio/src/iosTest/kotlin/ge/yet/game/miniapp/audio/internal/IosPcmProducerTest.kt miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/CompiledAudioRuntime.kt miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/internal/CompiledAudioRuntimeTest.kt
git commit -m "feat: render iOS PCM through a bounded producer"
```

## Task 4: Add the real Foundation worker lifecycle

**Files:**

- Create: `miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal/IosProducerThread.kt`
- Create: `miniapp/audio/src/iosTest/kotlin/ge/yet/game/miniapp/audio/internal/IosProducerThreadTest.kt`
- Modify: `miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal/IosPcmProducer.kt`
- Modify: `miniapp/audio/src/iosTest/kotlin/ge/yet/game/miniapp/audio/internal/IosPcmProducerTest.kt`

- [x] **Step 1: Write the Foundation compile/lifecycle RED test**

Create a bounded test that starts a worker, increments an atomic value inside
its block, requests termination and waits for completion. Every wait must use a
one-second deadline and fail rather than hang the simulator test process.

- [x] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :miniapp:audio:iosSimulatorArm64Test --tests '*IosProducerThreadTest*'
```

Expected: compilation fails because `FoundationIosProducerThread` does not exist.

- [x] **Step 3: Implement the narrow thread boundary**

Use an interface so all producer state tests remain deterministic:

```kotlin
internal interface IosProducerThread {
    fun start(block: () -> Unit)
    fun signal()
    fun awaitSignal(timeoutSeconds: Double)
    fun awaitTermination(timeoutSeconds: Double): Boolean
    fun markTerminated()
}

internal fun interface IosProducerThreadFactory {
    fun create(): IosProducerThread
}
```

`FoundationIosProducerThread` owns one `NSCondition`, starts exactly once with
`NSThread.detachNewThreadWithBlock`, uses `waitUntilDate` for bounded waits,
signals/broadcasts while holding the condition and records terminal state before
broadcast. It contains no audio runtime or callback logic.

- [x] **Step 4: Connect the worker loop to the producer**

Start the producer thread once during producer construction. The loop repeatedly
calls `pumpOnce()`. When no block can be produced, call `awaitSignal` with a
timeout derived from:

```kotlin
val bufferedSeconds = ring.availableFrames.toDouble() / sampleRate.toDouble()
val waitSeconds = (bufferedSeconds / 2.0).coerceIn(0.001, 0.010)
```

Signal after accepted/coalesced command submission, policy change and resume.
`resumeAndAwaitPrefill` waits on the producer condition until the start
watermark, terminal failure or termination. `pauseAndReset` waits for the
current pump to exit, then waits until `callbackSource.hasCallbackInFlight()` is
false before resetting the ring. The caller must pause/stop the engine before
this barrier, so no new callback can enter. Use a bounded timed wait rather than
having the callback signal a condition. `terminate` wakes the loop and waits for
its terminal broadcast before destroying the renderer.

- [x] **Step 5: Add real-thread producer integration assertions**

Using the real thread factory, verify accepted Play reaches the start watermark,
pause clears it, resume prefills again and termination completes within one
second. Do not assert exact wakeup counts because native scheduling is nondeterministic.

- [x] **Step 6: Run iOS tests and compile the iOS source set**

Run:

```bash
./gradlew :miniapp:audio:iosSimulatorArm64Test :miniapp:audio:compileKotlinIosSimulatorArm64
```

Expected: tests pass and Kotlin/Native accepts all Foundation signatures.

- [x] **Step 7: Commit the worker boundary**

```bash
git add miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal/IosProducerThread.kt miniapp/audio/src/iosTest/kotlin/ge/yet/game/miniapp/audio/internal/IosProducerThreadTest.kt miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal/IosPcmProducer.kt miniapp/audio/src/iosTest/kotlin/ge/yet/game/miniapp/audio/internal/IosPcmProducerTest.kt
git commit -m "feat: run iOS PCM producer on a session thread"
```

## Task 5: Integrate producer prefill and lifecycle into the iOS sink

**Files:**

- Modify: `miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal/IosAudioSink.kt`
- Modify: `miniapp/audio/src/iosTest/kotlin/ge/yet/game/miniapp/audio/internal/IosAudioSinkTest.kt`

- [x] **Step 1: Replace renderer-based sink fakes with a recording producer**

Create `RecordingIosPcmProducer` implementing `IosPcmProducerSession`. It records
this ordered event vocabulary:

```kotlin
enum class ProducerEvent {
    Submit,
    UpdatePolicy,
    ResumeAndPrefill,
    PauseAndReset,
    DrainDiagnostics,
    Terminate,
}
```

The fake exposes its own `IosPcmCallbackSource` backed by a small ring and lets
tests choose whether prefill succeeds.

- [x] **Step 2: Write RED lifecycle-order tests**

Update/add assertions proving:

- Play order is `Submit -> session active true -> ResumeAndPrefill -> engine start`;
- failed prefill does not start the engine and deactivates the session;
- background/interruption order is `engine pause -> PauseAndReset -> session active false`;
- resumable interruption prefills before restart;
- non-resumable interruption waits for a new explicit Play;
- route change pauses/quiesces, resets engine, prefills and restarts;
- media reset terminates no session producer, but pauses/resets its PCM, rebuilds
  only the native engine and then prefills/restarts;
- release order is `engine pause/stop -> producer terminate -> engine release -> observation remove -> session inactive`;
- release remains idempotent and later commands return `RejectedDestroyed`.

- [x] **Step 3: Run sink tests and verify RED**

Run:

```bash
./gradlew :miniapp:audio:iosSimulatorArm64Test --tests '*IosAudioSinkTest*'
```

Expected: existing implementation starts the engine before producer prefill and
does not expose the required producer lifecycle events.

- [x] **Step 4: Refactor sink construction and ownership**

Change the sink constructor to inject a producer factory:

```kotlin
internal class IosAudioSink(
    private val platform: IosAudioPlatform,
    private val rendererFactory: IosAudioRendererFactory = IosAudioRendererFactory(::DefaultIosAudioRenderer),
    private val producerFactory: IosPcmProducerFactory = IosPcmProducerFactory(::createDefaultIosPcmProducer),
) : PlatformAudioSink
```

Define `createDefaultIosPcmProducer(sampleRate, maximumFramesPerSlice,
rendererFactory)` as the three-argument top-level adapter that constructs
`DefaultIosPcmProducer` with `FoundationIosProducerThread`. Tests inject their
own `IosPcmProducerFactory`; do not depend on constructor-reference adaptation
of a fourth default thread-factory argument.

`IosAudioSinkSession` creates one producer, passes
`producer.callbackSource` to `platform.createEngine`, and delegates submit,
policy and diagnostics. Remove direct `renderer`, `runtime`, callback lock,
and callback-underrun ownership from the session. Retain one session-owned
`backendFailures` atomic for audio-session and engine lifecycle failures; these
calls are outside the callback but must remain diagnosable.

- [x] **Step 5: Implement generation-checked reconciliation**

Keep desired state under the session `NSLock`, but never hold it while waiting
for producer prefill/termination or invoking engine lifecycle operations.
Represent each transition with immutable primitives and an engine reference:

```kotlin
private data class OutputTransition(
    val generation: Long,
    val shouldRun: Boolean,
    val engine: IosAudioEngine,
)
```

Every policy/event/Play/release mutation increments `transitionGeneration`.
One reconciler at a time snapshots `OutputTransition`, unlocks, executes the
pause/prefill action, then re-locks and commits `outputRunning` only when the
generation and engine identity still match. If a newer generation exists, loop
again immediately. `release` invalidates the generation, waits for any active
reconciler through a session `NSCondition`, then performs the required teardown
order exactly once.

- [x] **Step 6: Aggregate producer and callback diagnostics outside callback**

`drainDiagnostics` combines:

```kotlin
val common = producer.drainRuntimeDiagnostics()
val callback = producer.callbackSource.drainDiagnostics()
val producerDetails = producer.drainProducerDiagnostics()
AudioRuntimeDiagnosticsSnapshot(
    validationRejections = common.validationRejections,
    queueOverflows = common.queueOverflows,
    forcedVoiceShedding = common.forcedVoiceShedding,
    callbackFailures = saturatedAdd(
        common.callbackFailures,
        saturatedAdd(
            callback.callbackFailures,
            saturatedAdd(producerDetails.renderFailures, backendFailures.exchange(0)),
        ),
    ),
    underruns = saturatedAdd(common.underruns, callback.underrunEvents),
)
```

Drain producer-only wakeup/render/peak details internally without expanding the
public snapshot. Producer render failures contribute to callback-failure
aggregation because that is the existing compatible failure bucket.

- [x] **Step 7: Run the iOS sink suite and compilation**

Run:

```bash
./gradlew :miniapp:audio:iosSimulatorArm64Test :miniapp:audio:compileKotlinIosSimulatorArm64
```

Expected: all lifecycle order and existing platform configuration tests pass.

- [x] **Step 8: Commit sink integration**

```bash
git add miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal/IosAudioSink.kt miniapp/audio/src/iosTest/kotlin/ge/yet/game/miniapp/audio/internal/IosAudioSinkTest.kt
git commit -m "refactor: isolate iOS audio callback from DSP"
```

## Task 6: Harden the native callback boundary

**Files:**

- Modify: `miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal/IosAudioSink.kt`
- Modify: `miniapp/audio/src/iosTest/kotlin/ge/yet/game/miniapp/audio/internal/IosAudioSinkTest.kt`

- [x] **Step 1: Write RED callback adapter tests**

Extend the fake native-engine seam so tests can request zero, partial and
multi-chunk frame counts. Assert:

- requested frames are copied from preallocated bridge arrays to both native channels;
- a request larger than the bridge capacity drains in bounded chunks;
- missing PCM is zero;
- zero frames is a successful no-op;
- a null `AudioBufferList`, fewer than two buffers or null channel data records
  one callback failure and returns success without throwing.

- [x] **Step 2: Run the callback tests and verify RED**

Run:

```bash
./gradlew :miniapp:audio:iosSimulatorArm64Test --tests '*IosAudioSinkTest*'
```

Expected: malformed-buffer cases exercise `check`/`checkNotNull` and the old
callback path still delegates DSP rendering.

- [x] **Step 3: Replace throwing validation with explicit branches**

In `FrameworkIosAudioEngine.renderCallback`, use early nullable/size checks and
`clearOutput`; do not use `check`, `require`, `checkNotNull`, string templates,
`runCatching` or collection helpers inside the callback. Keep the hot loop as
primitive indexed copies from the fixed left/right bridge arrays populated by
`IosPcmCallbackSource.render`.

The callback catch remains a final containment boundary for unforeseen native
interop failures, but expected malformed inputs follow non-throwing branches.

- [x] **Step 4: Run the source boundary scan**

Run:

```bash
rg -n 'tryLock|NSLock|NSCondition|consumeCommandsForBlock|renderer\.render|CycleTime|TimeArc|launch|suspend|println|Logger|Crashlytics|runCatching|checkNotNull|check\(' miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal
```

Expected: lifecycle locks and producer-side renderer/runtime calls remain in
their dedicated files; none of the forbidden operations appears inside
`FrameworkIosAudioEngine.renderCallback` or `IosPcmCallbackSource.render`.

- [x] **Step 5: Run all audio tests**

Run:

```bash
./gradlew :core:pattern:allTests :miniapp:audio:allTests
```

Expected: Android/JVM and iOS audio tests pass.

- [x] **Step 6: Commit callback hardening**

```bash
git add miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal/IosAudioSink.kt miniapp/audio/src/iosTest/kotlin/ge/yet/game/miniapp/audio/internal/IosAudioSinkTest.kt
git commit -m "perf: harden iOS realtime callback boundary"
```

## Task 7: Document the architecture and run integration gates

**Files:**

- Modify: `AGENTS.md`
- Modify: `docs/superpowers/plans/2026-08-23-miniapp-procedural-audio.md`
- Modify only source/tests required by failures exposed in this task.

- [x] **Step 1: Update repository guidance**

Change the `:miniapp:audio` row to state that iOS uses a session-owned producer
thread and fixed SPSC PCM ring, while `AVAudioSourceNode` only drains prepared
PCM. Add one procedural-audio paragraph stating that scheduling/DSP allocations
are permitted only on the producer side, never in the native callback. Do not
change contributor-facing authoring instructions.

- [x] **Step 2: Link the sub-plan from the parent Task 15**

Add this sentence under Task 15:

```markdown
The iOS absolute realtime boundary is implemented and verified through
`docs/superpowers/plans/2026-08-24-ios-realtime-pcm-producer.md`.
```

Mark only the realtime source-audit checkbox complete after the scan and tests
actually pass. Leave physical-device Instruments work unchecked unless it was run.

- [x] **Step 3: Run narrow platform gates**

Run:

```bash
./gradlew \
  :core:pattern:allTests \
  :core:pattern:compileAndroidMain \
  :core:pattern:compileKotlinIosSimulatorArm64 \
  :miniapp:audio:allTests \
  :miniapp:audio:testAndroidHostTest \
  :miniapp:audio:compileAndroidMain \
  :miniapp:audio:compileKotlinIosSimulatorArm64 \
  :miniapp:audio-presets:allTests \
  :miniapp:audio-presets:compileAndroidMain \
  :miniapp:audio-presets:compileKotlinIosSimulatorArm64
```

Expected: BUILD SUCCESSFUL.

- [x] **Step 4: Run the consuming iOS framework gate**

Run:

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

Expected: the ComposeApp simulator framework links successfully with the new
Foundation and AVFAudio implementation.

- [x] **Step 5: Run deterministic render regression checks**

Run the existing offline-render tests twice on JVM and iOS:

```bash
./gradlew :miniapp:audio:testAndroidHostTest --tests '*OfflineAudioRendererTest*' :miniapp:audio:iosSimulatorArm64Test --tests '*OfflineAudioRendererTest*' --rerun-tasks
./gradlew :miniapp:audio:testAndroidHostTest --tests '*OfflineAudioRendererTest*' :miniapp:audio:iosSimulatorArm64Test --tests '*OfflineAudioRendererTest*' --rerun-tasks
```

Expected: both runs pass with the existing exact hashes/acoustic tolerances;
the iOS buffering refactor must not change shared renderer output.

- [x] **Step 6: Re-index and inspect the structural diff**

Refresh the codebase-memory index for
`/Users/yet/development/Multiplatform/BlockBlast`, then use graph search/trace to
verify that `FrameworkIosAudioEngine` reaches `IosPcmCallbackSource` and the
ring but has no path to `CompiledAudioRuntime` or `RealtimeAudioRenderer`.
Review `git diff` for accidental Android/public API/dependency changes.

- [x] **Step 7: Commit documentation and any verified scoped fixes**

```bash
git add AGENTS.md docs/superpowers/plans/2026-08-23-miniapp-procedural-audio.md
git commit -m "docs: record iOS PCM producer boundary"
```

- [ ] **Step 8: Report the physical-device gate honestly**

The paired iPhone 13 Pro was offline on 2026-08-24. Simulator tests and the
consuming framework gate passed, but audible device playback and Instruments
Allocations remain intentionally unverified.

If an unlocked iPhone is available, run Music/SFX, rapid SFX,
background/foreground, interruption and reopen smoke tests plus Instruments
Allocations. Otherwise report the device and absolute-allocation proof as the
only remaining manual gate; simulator success is not a substitute.

## Final Completion Conditions

- The native callback has no session lock, runtime command consumption,
  scheduler, pattern query or DSP call.
- One session producer owns all mutable runtime/renderer state.
- Prefill occurs before every permitted engine start.
- Pause/reset/release wait for callback and producer quiescence before clearing
  or destroying shared storage.
- Commands retain existing bounded rejection behavior and public API compatibility.
- All common, Android and iOS audio tests and compilation gates pass.
- The full procedural-audio Task 15 continues after this sub-plan with its
  dependency, configuration-cache, bundle and branch-wide release checks.
