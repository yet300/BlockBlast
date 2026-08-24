# iOS Realtime PCM Producer Design

**Status:** Approved design

## Goal

Make the `AVAudioSourceNode` render callback allocation-free and non-blocking by
moving command consumption, pattern scheduling and DSP rendering onto a
session-owned producer thread. The callback must only drain already-rendered
stereo PCM from a fixed single-producer/single-consumer ring buffer and write
silence when data is unavailable.

This design refines the iOS portion of
`2026-08-23-kotlin-pattern-audio-design.md`. Android keeps its dedicated
`AudioTrack` writer-thread architecture. The public `MiniAppAudio`,
`AudioProgram` and contributor authoring APIs do not change.

## Problem

The current iOS sink calls `CompiledAudioRuntime.consumeCommandsForBlock()` and
`RealtimeAudioRenderer.render()` from the `AVAudioSourceNode` callback while
holding an `NSLock` acquired with `tryLock`. Collection growth has been removed
from the renderer and scheduler, but a generic pattern query still creates
short-lived `CycleTime` and `TimeArc` values. The callback also performs command
dispatch and DSP work whose execution time is not bounded by the native copy
operation.

Avoiding collections alone is therefore insufficient. A native audio callback
must not depend on Kotlin object allocation, a contended session lock, pattern
evaluation or the duration of the complete synthesizer pipeline.

## Non-goals

- Changing the public MiniApp audio DSL or adding an iOS-specific public API.
- Moving shared scheduling or DSP code into Swift, Objective-C, C or C++.
- Changing the Android sink.
- Dynamically resizing buffers or automatically retuning latency in this stage.
- Reporting diagnostics directly to Crashlytics from `:miniapp:audio`.
- Guaranteeing that UI commands are audible in the immediately executing native
  callback. They become audible at the bounded buffered horizon.

## Architecture

One `IosPcmProducer` belongs to one active `IosAudioSinkSession`:

```text
MiniApp/UI thread
    |
    | bounded AudioCommand submission under session lock
    v
CompiledAudioRuntime command queue
    |
    | consumed only by IosPcmProducer
    v
RealtimeAudioRenderer -> reusable stereo work block
    |
    | single writer
    v
StereoPcmRingBuffer
    |
    | single reader
    v
AVAudioSourceNode callback -> native AudioBufferList
```

The producer exclusively owns `CompiledAudioRuntime`, `AudioScheduler` and
`RealtimeAudioRenderer`. UI/session methods may submit commands but may not
consume them or invoke the renderer. The native callback may access only the
ring buffer and preallocated atomic diagnostics.

`updatePolicy` follows the same ownership rule: the session stores the desired
policy and wakes the producer; only the producer applies that policy to the
renderer before producing another block.

The producer lifecycle is iOS-specific and remains in `iosMain`. The fixed
stereo SPSC ring is an internal `commonMain` primitive because it has no Apple
dependency and can be verified with portable deterministic tests. Neither type
is exposed to MiniApps.

## Buffer Model

`StereoPcmRingBuffer` owns two fixed `FloatArray` instances, one per channel,
and monotonic atomic read/write frame positions. The producer is the only
writer and the native callback is the only reader.

Publication order is:

1. Producer writes samples into both channel arrays.
2. Producer publishes the new write position with an atomic store.
3. Callback loads the write position, copies only published frames, then
   publishes its new read position.

The reverse positions determine available and free frames. No compare-and-set
loop is needed for normal read or write because each side owns one position.
Atomic publication provides visibility for preceding array writes. Position
distance, not a reserved array slot, distinguishes empty from full.

The ring operations are bounded:

- `write(left, right, frameCount)` writes at most the reported free frames and
  never overwrites unread samples;
- `readOrSilence(left, right, frameCount)` copies available samples, fills the
  remaining requested range with zero and returns the missing-frame count;
- wrap-around is handled with at most two primitive copy ranges per channel;
- `reset()` is legal only after the engine callback and producer are quiescent;
- construction is the only operation that allocates sample arrays.

## Quantum, Capacity and Latency

The current platform layer uses a conservative `maximumFramesPerSlice` of
4,096. Treating that value as the normal render quantum and buffering eight
such blocks would add roughly 683 ms at 48 kHz. The implementation therefore
separates the maximum callback request from the producer quantum:

```text
producerQuantum = clamp(maximumFramesPerSlice, 64, 512)
ringCapacity = nextPowerOfTwo(max(8 * producerQuantum, maximumFramesPerSlice))
startWatermark = min(ringCapacity, max(3 * producerQuantum, maximumFramesPerSlice))
targetWatermark = min(ringCapacity, max(6 * producerQuantum, maximumFramesPerSlice))
```

For a normal 512-frame callback this produces a 4,096-frame ring, a 1,536-frame
start watermark and a 3,072-frame target. At 48 kHz these are approximately
32 ms startup reserve, 64 ms target latency and 85 ms total capacity.

With the current conservative 4,096-frame maximum, the same ring can satisfy
one maximum-sized callback after prefill without growing to eight maximum
slices. The start watermark then becomes the full ring. A later platform change
may derive the real maximum from the Audio Unit, but it is not required for the
correctness of this design.

The values are private backend policy, not API guarantees. They may be tuned
after device profiling without changing a game or its audio program.

## Producer Thread and Waiting

`IosPcmProducer` runs a single session-owned `NSThread`. It creates no coroutine
scope or dispatcher. A small internal wait abstraction allows deterministic
tests to pump the producer without starting a real thread.

The producer loop:

1. Waits while stopped, paused, failed or already at the target watermark.
2. Acquires the session command lock outside the native callback.
3. Consumes the bounded command batch and snapshots the current policy.
4. Releases the session lock.
5. Renders one reusable `producerQuantum` stereo block.
6. Publishes as many frames as the ring can accept; free capacity is checked
   before rendering, so a normal iteration publishes the complete block.
7. Repeats until the target watermark is reached.

When the ring is sufficiently full, the producer performs a timed wait derived
from buffered frames and sample rate. It does not spin continuously. UI command
submission signals the producer because this occurs outside the native
callback. The callback never locks or signals a condition; the producer's
bounded timed wait is the only mechanism needed after PCM consumption.

An `NSCondition` guards producer run/stop/termination state. `release()` can
therefore request termination and wait for the terminal signal even though
`NSThread` has no join API. The condition is never visible to the native
callback.

No method waits for prefill or producer termination while holding the session
command lock. A lifecycle transition receives a monotonically increasing
generation under that lock, performs its blocking barrier after unlocking, then
rechecks the generation before starting an engine. A newer pause, interruption
or release therefore invalidates an older in-progress start instead of racing
it.

## Native Callback Boundary

`FrameworkIosAudioEngine` continues to own the `AVAudioSourceNode` and its
preallocated bridge buffers. Its callback performs only these operations:

1. Validate the provided native buffer pointers using non-allocating branches.
2. Drain the requested frames from `StereoPcmRingBuffer` into the existing
   preallocated left/right bridge arrays, including wrap-around.
3. Copy those primitive samples into the native left/right buffers.
4. Fill any missing suffix with zero.
5. Atomically add missing frames and, when nonzero, one underrun event.
6. Return success.

The callback must not:

- acquire `NSLock`, `NSCondition`, mutexes or monitors;
- submit or consume `AudioCommand` values;
- call `CompiledAudioRuntime`, patterns, scheduler or DSP renderer;
- construct `CycleTime`, `TimeArc`, collections, strings or exceptions;
- invoke logging, telemetry, DI, storage, coroutines or contributor callbacks;
- execute `runCatching` or use lambdas introduced by convenience helpers.

Malformed native buffers are cleared when safely addressable and counted as a
callback failure. No exception escapes the callback. The callback does not
attempt recovery or reconfiguration.

An atomic in-flight callback count is incremented on entry and decremented on
every exit. After pausing/stopping the engine, ordinary session code waits for
that count to reach zero before resetting the ring. The callback never signals
the waiter or participates in a condition variable.

## Session Lifecycle

The session state machine preserves existing audio policy and interruption
semantics while enforcing quiescent buffer resets.

### Open

1. Configure `AVAudioSession`.
2. Allocate renderer, runtime, work block, ring and counters.
3. Create the idle producer thread.
4. Create and prepare `AVAudioEngine` and `AVAudioSourceNode`.
5. Register interruption, route and media-service observations.

Opening a session does not activate or start audio output.

### First play and resume

1. Submit the accepted command and mark output requested.
2. Activate the audio session.
3. Start or resume the producer.
4. Wait outside the native callback until `startWatermark` is reached or the
   producer reports failure.
5. Start `AVAudioEngine` only after successful prefill.

The synchronous prefill is bounded by ring capacity and program budgets. It
prevents the engine's first callback from racing an empty producer.

### Pause, background and interruption

1. Pause the engine so the callback becomes quiescent.
2. Pause the producer and wait until its current block is no longer publishing.
3. Reset the ring.
4. Deactivate the audio session where the existing policy requires it.

This ordering prevents stale buffered music or SFX from playing after resume.
A resumable interruption follows the prefill/start sequence. A non-resumable
interruption remains blocked until a new explicit play request, matching current
behavior.

### Route change

Pause the engine, quiesce the producer, reset the ring and engine, then prefill
and restart only if output was running before the route change.

### Media-services reset

Quiesce producer and callback, release the invalid engine, reset the ring,
reconfigure the audio session, create the replacement engine, then use the
normal prefill/start sequence when policy still permits output.

### Release

1. Mark the session released so later commands are rejected.
2. Pause and stop the engine, making the callback quiescent.
3. Request producer termination and wait for its terminal signal.
4. Remove observations and release the engine.
5. Deactivate the audio session.
6. Destroy the renderer/runtime state.

Release is idempotent. Producer termination completes before renderer
destruction, so no stale thread can write to a released session.

## Commands and Audible Horizon

The existing bounded `CompiledAudioRuntime` queue remains the command ingress.
Only producer ownership changes. Queue overflow keeps the existing typed
rejection behavior; commands are not silently displaced.

Accepted commands are applied before the producer renders its next block.
Already-buffered PCM is not rewritten. Therefore Play, SFX, Stop and control
changes have an additional delay bounded by the buffered horizon, normally no
more than approximately 64 ms with the initial policy. Clearing the ring for
every command would reduce that delay but would create discontinuities and
complicate SPSC ownership, so it is explicitly rejected.

Pause, interruption and release are lifecycle barriers rather than ordinary
audio commands and do clear buffered data after both sides are quiescent.

## Failures and Diagnostics

The callback owns only preallocated atomic counters:

- rendered frames;
- underrun frames;
- underrun events;
- malformed callback failures.

Callback counters use atomic fetch-add operations rather than saturating CAS
loops. Diagnostic draining converts deltas into the existing saturated public
snapshot outside the callback. Counter wraparound is handled as modular delta
arithmetic and does not alter audio behavior.

The producer additionally tracks producer wakeups, render failures and peak
buffered frames. These details remain internal; the existing
`AudioRuntimeDiagnosticsSnapshot` receives the compatible aggregated underrun
and callback-failure values.

A single underrun is recoverable: the missing suffix is silence and playback
continues. The callback never changes buffer policy. Repeated underruns may be
reported later by the ordinary host diagnostic path, but do not allocate or log
from the callback.

An exception during command consumption or DSP rendering is caught by the
producer. The current work block is cleared, the producer enters a terminal
failed state and stops publishing. The callback consequently produces silence.
The failure is exposed through the existing internal diagnostics outside the
callback. An explicit close and newly opened session are required for recovery;
later Play calls do not secretly revive a failed producer.

Normal pause, interruption, route handling and release are not failures.
`:miniapp:audio` gains no direct dependency on telemetry or Crashlytics.

## Testing

### Portable ring tests

Common tests cover stereo ordering, partial reads and writes, wrap-around,
full/empty distinction, refusal to overwrite unread frames, silence filling,
missing-frame reporting, reset and reuse of the same arrays.

### Deterministic producer tests

An injected manual pump/wait boundary verifies:

- prefill reaches the start watermark before engine start;
- rendering stops at the target watermark;
- commands are consumed by producer work, never callback work;
- pause quiesces publishing and clears buffered PCM;
- resume prefills before restart;
- render failure enters terminal failure and publishes no partial block;
- release waits for termination and forbids later writes.

One iOS integration test uses the real `NSThread` lifecycle to prove start,
wake, pause and synchronous termination.

### Callback tests

The fake engine invokes the same callback adapter and verifies prepared PCM,
wrap-around, partial underrun silence, aggregated diagnostics and malformed
buffer containment. Recording fakes assert that callback execution never calls
the runtime, renderer, command queue or session lock.

### Realtime audit

Source inspection must prove that the native callback contains no scheduling,
DSP, logging, suspension, storage, DI, string formatting, mutable collection
growth or locking. Unit tests cannot prove the complete absence of
Kotlin/Native allocation, so a physical-device Instruments Allocations run is
the final evidence for the absolute callback boundary.

### Build and behavior gates

Run:

```bash
./gradlew :core:pattern:allTests
./gradlew :miniapp:audio:allTests
./gradlew :miniapp:audio:iosSimulatorArm64Test
./gradlew :miniapp:audio:compileAndroidMain :miniapp:audio:compileKotlinIosSimulatorArm64
./gradlew :miniapp:audio-presets:allTests
./gradlew :miniapp:audio-presets:compileAndroidMain :miniapp:audio-presets:compileKotlinIosSimulatorArm64
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

The physical-device smoke test verifies audible Music/SFX, rapid SFX, audio
settings, background/foreground, interruption recovery, close/reopen behavior,
absence of obvious clicks and zero callback allocations or locks in Instruments.

## Acceptance Criteria

- `AVAudioSourceNode` drains only fixed PCM storage and atomic counters.
- Runtime command consumption, pattern scheduling and DSP execute only on the
  session producer thread.
- Callback underrun produces bounded silence without escaping an exception.
- Pause, interruption, route reset, media reset and release cannot replay stale
  PCM or leave a producer touching destroyed state.
- Ring and producer behavior have deterministic portable/iOS tests.
- Existing public authoring APIs and Android playback remain source-compatible.
- Simulator build/tests pass, and the remaining physical-device allocation
  proof is reported explicitly if it cannot be run in the development session.
