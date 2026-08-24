# MiniApp Procedural Audio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a clean-room Kotlin Multiplatform pattern engine and host-managed procedural Music/SFX capability that games can author entirely in Kotlin, with reusable original presets and realtime Android/iOS output.

**Architecture:** `:core:pattern` owns exact, generic temporal queries. `:miniapp:audio` owns the immutable public audio DSL, compiler, bounded scheduler/DSP, app-scoped engine and platform sinks. `:miniapp:audio-presets` contains only reusable declarations built on the public API. Root opens one stale-safe audio facade per MiniApp session and exposes it through `MiniAppSessionContext`; Settings, lifecycle and platform audio remain host-owned.

**Tech Stack:** Kotlin 2.4.10, Kotlin Multiplatform, kotlinx.coroutines, Metro 1.4.1, Decompose/Essenty lifecycle, Android `AudioTrack`, iOS `AVAudioSession`/`AVAudioEngine`/`AVAudioSourceNode`, kotlin-test.

---

## Global implementation rules

- Work only on `codex/miniapp-plugin-framework`; do not create a worktree.
- Follow RED → GREEN → refactor for every task. A compilation failure counts as RED only when it is caused by the missing production contract under test.
- Do not copy, translate or adapt Klangmotör, Strudel, Sprudel or third-party songs, presets, declarations, tests or source. Use only standard DSP mathematics, public platform documentation and the approved design.
- Never run contributor code, DI, storage, logging, suspension or collection allocation from the realtime callback.
- Keep the host's legacy `AudioRepository` working during this project. Task 13 migrates Block Blast itself to session-scoped procedural audio; removal of the remaining host compatibility path is a later decision.
- `MiniAppVisibility.BACKGROUND` is the concrete implementation of the design's inactive state.
- Run `git diff --check` before every commit and preserve unrelated changes.

## Task 1: Scaffold the three modules and freeze dependency direction

**Files:**

- Modify: `settings.gradle.kts`
- Modify: `AGENTS.md`
- Create: `core/pattern/build.gradle.kts`
- Create: `miniapp/audio/build.gradle.kts`
- Create: `miniapp/audio-presets/build.gradle.kts`

- [ ] Treat this Gradle-only scaffold as the explicit TDD exception; it adds no runtime behavior. The first pattern behavior RED is Task 2 and the first audio API RED is Task 4.
- [ ] Include the modules and run the configuration/empty-source compile gate:

  ```bash
  ./gradlew :core:pattern:compileTestKotlinIosSimulatorArm64 \
    :miniapp:audio:compileTestKotlinIosSimulatorArm64 \
    :miniapp:audio-presets:compileTestKotlinIosSimulatorArm64
  ```

  Expected: all three empty modules configure and compile successfully.

- [ ] Include `:core:pattern`, `:miniapp:audio`, and `:miniapp:audio-presets` explicitly in `settings.gradle.kts`.
- [ ] Configure dependencies exactly as follows:

  ```text
  core:pattern          -> no project dependency
  miniapp:audio         -> core:pattern, miniapp:api, core:domain, core:common
  miniapp:audio-presets -> miniapp:audio only
  ```

  `:miniapp:audio` may apply Metro and coroutines, but must not depend on `:miniapp:compose`, `:miniapp:metro`, features, games, Compose, Settings implementation, Firebase or platform app modules.

- [ ] Add the three modules and their boundaries to `AGENTS.md`. Do not claim platform playback yet.
- [ ] Run `./gradlew projects` and the three compile tasks again; later tasks will make them GREEN.
- [ ] Commit:

  ```bash
  git add settings.gradle.kts AGENTS.md core/pattern miniapp/audio miniapp/audio-presets
  git commit -m "build: scaffold procedural audio modules"
  ```

## Task 2: Implement exact generic pattern time and bounded queries

**Files:**

- Create: `core/pattern/src/commonMain/kotlin/ge/yet/game/pattern/CycleTime.kt`
- Create: `core/pattern/src/commonMain/kotlin/ge/yet/game/pattern/TimeArc.kt`
- Create: `core/pattern/src/commonMain/kotlin/ge/yet/game/pattern/PatternEvent.kt`
- Create: `core/pattern/src/commonMain/kotlin/ge/yet/game/pattern/Pattern.kt`
- Create: `core/pattern/src/commonMain/kotlin/ge/yet/game/pattern/PatternQuery.kt`
- Create: `core/pattern/src/commonTest/kotlin/ge/yet/game/pattern/CycleTimeTest.kt`
- Create: `core/pattern/src/commonTest/kotlin/ge/yet/game/pattern/PatternQueryTest.kt`

- [ ] Write RED tests for normalized rational equality, negative values, overflow-safe comparison, half-open arcs, clipped event intersections, stable event order and query-budget exhaustion.
- [ ] Define the public primitives:

  ```kotlin
  data class CycleTime private constructor(val numerator: Long, val denominator: Long) : Comparable<CycleTime>

  data class TimeArc(val start: CycleTime, val endExclusive: CycleTime)

  data class PatternEvent<out T>(
      val whole: TimeArc,
      val active: TimeArc,
      val value: T,
  )

  fun interface Pattern<T> {
      fun query(arc: TimeArc, budget: PatternQueryBudget = PatternQueryBudget.Default): List<PatternEvent<T>>
  }
  ```

  Construction must normalize sign/GCD, reject denominator zero, and use checked arithmetic. Invalid or exhausted queries return a typed `PatternQueryResult` at internal evaluation boundaries; public convenience `query` throws a stable `PatternQueryException`, never loops indefinitely.

- [ ] Implement a mutable internal `PatternQueryBudget` counter passed through nested queries. Default maximum events is 256 and default maximum operations is 4096.
- [ ] Run:

  ```bash
  ./gradlew :core:pattern:allTests
  ```

- [ ] Add property-style loops for normalization and exact boundary composition without an external property-testing dependency.
- [ ] Commit:

  ```bash
  git add core/pattern
  git commit -m "feat: add exact bounded pattern queries"
  ```

## Task 3: Add generic pattern combinators and deterministic randomness

**Files:**

- Create: `core/pattern/src/commonMain/kotlin/ge/yet/game/pattern/Patterns.kt`
- Create: `core/pattern/src/commonMain/kotlin/ge/yet/game/pattern/PatternTransforms.kt`
- Create: `core/pattern/src/commonMain/kotlin/ge/yet/game/pattern/PatternRandom.kt`
- Create: `core/pattern/src/commonMain/kotlin/ge/yet/game/pattern/EuclideanPattern.kt`
- Create: `core/pattern/src/commonTest/kotlin/ge/yet/game/pattern/PatternTransformsTest.kt`
- Create: `core/pattern/src/commonTest/kotlin/ge/yet/game/pattern/PatternRandomTest.kt`
- Create: `core/pattern/src/commonTest/kotlin/ge/yet/game/pattern/EuclideanPatternTest.kt`
- Modify: `miniapp/samples/counter/build.gradle.kts`
- Create: `miniapp/samples/counter/src/commonTest/kotlin/ge/yet/sample/counter/CounterPulsePatternTest.kt`

- [ ] RED-test `pure`, `sequence`, `stack`, `repeat`, `shift`, `slow`, `fast`, `every`, `choose`, `degrade` and Euclidean rhythm across negative and multi-cycle arcs.
- [ ] Implement immutable combinators as `Pattern<T>` wrappers. Nested wrappers must share the caller's operation/event budget rather than resetting it.
- [ ] Implement a repository-owned deterministic PRNG/hash keyed by `(seed, exact cycle, event ordinal)`. Same seed/query must yield the same result independent of query chunking; different seeds must diverge.
- [ ] Reject non-positive speed/repeat values and invalid Euclidean pulses/steps at construction.
- [ ] Add `:core:pattern` as a Counter `commonTest` dependency and prove a non-audio pulse/step schedule with `Pattern<Int>`. This is an unshipped reference ensuring the core API remains useful without notes, instruments or PCM.
- [ ] Run:

  ```bash
  ./gradlew :core:pattern:allTests :core:pattern:compileAndroidMain \
    :core:pattern:compileKotlinIosSimulatorArm64
  ```

- [ ] Commit:

  ```bash
  git add core/pattern
  git commit -m "feat: add reusable pattern combinators"
  ```

## Task 4: Define the immutable public procedural-audio API and compiler diagnostics

**Files:**

- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/AudioValues.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/AudioProgram.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/AudioProgramDsl.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/AudioDeclarations.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/AudioValidation.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/MiniAppAudio.kt`
- Create: `miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/AudioProgramDslTest.kt`
- Create: `miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/AudioValidationTest.kt`

- [ ] RED-test value types, typed lookup failures, duplicate names, unresolved references, bad ranges, graph depth and every approved mobile budget.
- [ ] Define validated value types (`AudioDuration`, `Frequency`, `Gain`, `Pan`, `Tempo`, `MidiNote`, `AudioControlName`, `InstrumentName`, `SfxName`) with public factory/extension functions such as `120.hz` and `40.ms`. Do not expose invalid public constructors.
- [ ] Define immutable declarations for oscillator/noise, additive partials, ADSR, pitch/FM/LFO, low/high/band-pass filters, distortion, bit crush, delay/reverb sends, instrument, music track, SFX and runtime controls.
- [ ] Define the author surface:

  ```kotlin
  fun audioProgram(block: AudioProgramBuilder.() -> Unit): AudioProgram

  interface MiniAppAudio {
      fun playMusic(program: AudioProgram): AudioCommandResult
      fun stopMusic(fadeOut: AudioDuration = AudioDuration.DefaultFade): AudioCommandResult
      fun playSfx(program: AudioProgram, name: SfxName): AudioCommandResult
      fun setControl(name: AudioControlName, value: Float): AudioCommandResult
  }
  ```

  `AudioCommandResult` is a sealed typed result; gameplay never catches backend exceptions.

- [ ] Implement a pure compiler/validator returning either `CompiledAudioProgram` (internal) or ordered `AudioDiagnostic` values with stable paths such as `musicTrack[bass].instrument[analog-bass].filter[0].cutoffHz`.
- [ ] Enforce: 32 voices, 8 SFX reserved, 16 tracks, 8 oscillators/instrument, 4 effects/track or bus, 4-second delay, feedback <= 0.95, 256 events/query, bounded depth/operations/queue.
- [ ] Run:

  ```bash
  ./gradlew :miniapp:audio:allTests
  ```

- [ ] Commit:

  ```bash
  git add miniapp/audio
  git commit -m "feat: define procedural audio authoring API"
  ```

## Task 5: Build deterministic offline DSP primitives

**Files:**

- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/dsp/AudioBuffer.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/dsp/Oscillator.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/dsp/Noise.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/dsp/Envelope.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/dsp/Filter.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/dsp/Modulator.kt`
- Create: `miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/internal/dsp/OscillatorTest.kt`
- Create: `miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/internal/dsp/NoiseTest.kt`
- Create: `miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/internal/dsp/EnvelopeTest.kt`
- Create: `miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/internal/dsp/FilterTest.kt`

- [ ] RED-test dominant frequency and bounded amplitude for sine/triangle/saw/square/pulse; deterministic and spectral behavior for white/pink/brown noise; ADSR phase boundaries; stable low/high/band-pass output; LFO and seeded smooth value-noise continuity.
- [ ] Use stereo non-interleaved preallocated `FloatArray` blocks internally. All processors render into caller-owned buffers.
- [ ] Give every oscillator/filter/envelope a mutable internal state object allocated when a voice is created, never while rendering a sample/block.
- [ ] Implement phase accumulation using the actual sample rate, sanitize non-finite samples to silence, and clamp unstable filter coefficients before entering the renderer.
- [ ] Use tolerances for cross-platform floating-point assertions. Hash only explicitly quantized offline PCM, not raw `Float` bytes.
- [ ] Run:

  ```bash
  ./gradlew :miniapp:audio:allTests
  ```

- [ ] Commit:

  ```bash
  git add miniapp/audio
  git commit -m "feat: add deterministic audio dsp primitives"
  ```

## Task 6: Add voices, effects, buses, limiter and deterministic offline renderer

**Files:**

- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/dsp/Voice.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/dsp/Effects.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/dsp/BusMixer.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/dsp/Limiter.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/OfflineAudioRenderer.kt`
- Create: `miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/internal/OfflineAudioRendererTest.kt`
- Create: `miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/internal/dsp/EffectsTest.kt`
- Create: `miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/internal/dsp/BusMixerTest.kt`

- [ ] RED-test fixed pipeline ordering, FM/pitch envelope, additive partial limits, distortion, quantization/sample-rate reduction, delay tail, bounded algorithmic reverb, equal-power pan, smooth bus gain and final limiter.
- [ ] Implement the fixed voice pipeline from the design; do not create a contributor-defined node graph or callback API.
- [ ] Preallocate the maximum approved delay, bus and scratch buffers when compiling/starting a program. Reject a graph that exceeds the configured memory budget.
- [ ] Implement `OfflineAudioRenderer.render(program, request): OfflineAudioResult` for tests/docs only. It must reuse the same compiler, scheduler and DSP as realtime playback.
- [ ] Golden tests must assert frame count, peak, RMS, silence regions, dominant-frequency bands and quantized PCM hash for repository-owned tiny fixtures.
- [ ] Run all tests and both common platform compiles.
- [ ] Commit:

  ```bash
  git add miniapp/audio
  git commit -m "feat: render bounded procedural audio graphs"
  ```

## Task 7: Implement look-ahead scheduling, voice allocation and bounded commands

**Files:**

- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/AudioCommand.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/AudioCommandQueue.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/AudioScheduler.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/VoiceAllocator.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/CompiledAudioRuntime.kt`
- Create: `miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/internal/AudioCommandQueueTest.kt`
- Create: `miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/internal/AudioSchedulerTest.kt`
- Create: `miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/internal/VoiceAllocatorTest.kt`

- [ ] RED-test bounded FIFO ordering, coalesced intermediate controls, non-droppable Stop/Destroy, sample-accurate scheduler boundaries, SFX reservation and deterministic voice stealing.
- [ ] Implement a fixed-capacity command ring. No command contains lambdas or arbitrary objects requiring work in the callback; compile program data before enqueue.
- [ ] Preserve at least eight voices for SFX. Steal released, then quietest, oldest, then low-priority Music voices, with stable tie-breaking IDs.
- [ ] Limit renderer command consumption per block so UI command floods cannot starve PCM generation.
- [ ] Add preallocated diagnostic counters for validation rejection, queue overflow, forced voice shedding, callback failures and underruns. Drain them outside realtime later.
- [ ] Run `:miniapp:audio:allTests` and commit.

## Task 8: Add original shared instruments, SFX and soundscapes

**Files:**

- Create: `miniapp/audio-presets/src/commonMain/kotlin/ge/yet/game/miniapp/audio/presets/Instruments.kt`
- Create: `miniapp/audio-presets/src/commonMain/kotlin/ge/yet/game/miniapp/audio/presets/SoundEffects.kt`
- Create: `miniapp/audio-presets/src/commonMain/kotlin/ge/yet/game/miniapp/audio/presets/Soundscapes.kt`
- Create: `miniapp/audio-presets/src/commonMain/kotlin/ge/yet/game/miniapp/audio/presets/PresetControls.kt`
- Create: `miniapp/audio-presets/src/commonTest/kotlin/ge/yet/game/miniapp/audio/presets/InstrumentsTest.kt`
- Create: `miniapp/audio-presets/src/commonTest/kotlin/ge/yet/game/miniapp/audio/presets/SoundEffectsTest.kt`
- Create: `miniapp/audio-presets/src/commonTest/kotlin/ge/yet/game/miniapp/audio/presets/SoundscapesTest.kt`

- [ ] RED-test public factories: `SoftPad`, `ChipLead`, `AnalogBass`, `GlassBell`, `PlacementClick`, `SuccessSweep`, `Explosion`, `PowerUp`, `OceanBreeze`, `SoftRain`, `ForestNight`, `DeepSpace`.
- [ ] Keep every declaration immutable and parameterized. Expose only documented seed/gain/density/stereo/control inputs and return public `AudioProgramFragment`/declarations from `:miniapp:audio`.
- [ ] Compose an original `OceanBreeze`: independently seeded colored-noise beds, slow band-pass/gain/stereo modulation and sparse additive chimes selected from an original scale-constrained pattern. Do not reuse the supplied third-party constants, arrangement or structure.
- [ ] Acoustic tests assert determinism, peak < 1, bounded RMS/event density/tails, stereo movement and monotonic response to each control. They must not compare against a third-party waveform.
- [ ] Prove the module cannot import `internal` audio runtime/DSP classes with a source scan and Gradle dependency report.
- [ ] Run:

  ```bash
  ./gradlew :miniapp:audio-presets:allTests \
    :miniapp:audio-presets:compileAndroidMain \
    :miniapp:audio-presets:compileKotlinIosSimulatorArm64
  ```

- [ ] Commit.

## Task 9: Create the app-scoped engine and stale-safe MiniApp session facade

**Files:**

- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/MiniAppAudioEngine.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/DefaultMiniAppAudio.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/AudioDiagnostics.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/internal/PlatformAudioSink.kt`
- Create: `miniapp/audio/src/commonMain/kotlin/ge/yet/game/miniapp/audio/di/MiniAppAudioBindings.kt`
- Modify: `miniapp/compose/build.gradle.kts`
- Modify: `miniapp/compose/src/commonMain/kotlin/ge/yet/game/miniapp/compose/MiniAppSessionContext.kt`
- Modify: `miniapp/metro/src/commonMain/kotlin/ge/yet/game/miniapp/metro/MiniAppSessionContextBindings.kt`
- Modify: `miniapp/testkit/src/commonMain/kotlin/ge/yet/game/miniapp/testkit/TestMiniAppSessionContext.kt`
- Modify: `feature/root/build.gradle.kts`
- Modify: `feature/root/src/commonMain/kotlin/ge/yet/game/feature/root/MiniAppRuntimeCoordinator.kt`
- Modify: `feature/root/src/commonMain/kotlin/ge/yet/game/feature/root/DefaultRootComponent.kt`
- Create: `miniapp/audio/src/commonTest/kotlin/ge/yet/game/miniapp/audio/internal/MiniAppAudioEngineTest.kt`
- Modify: `feature/root/src/commonTest/kotlin/ge/yet/game/feature/root/MiniAppRuntimeCoordinatorTest.kt`

- [ ] Add RED tests proving:
  - each session receives a distinct ID/key-bound facade;
  - old handles cannot control a replacement session;
  - `ACTIVE` plays normally, `OBSCURED` ducks Music and rejects new SFX, `BACKGROUND` pauses scheduling;
  - destroy releases voices/commands once and rejects later commands;
  - music/SFX Settings flows gate only their own buses;
  - backend/diagnostic failures cannot fail game/session creation.
- [ ] Add `val audio: MiniAppAudio` to `MiniAppSessionContext`; add a Metro provider beside storage. Update every context fake/anonymous implementation in one compile-driven pass.
- [ ] Define an app-scoped engine contract used by Root:

  ```kotlin
  interface MiniAppAudioEngine {
      fun openSession(
          id: MiniAppId,
          sessionKey: Long,
          lifecycle: Lifecycle,
          visibility: MiniAppVisibilitySource,
      ): MiniAppAudio
  }
  ```

  The engine implementation observes `SettingsRepository.musicEnabled` and `sfxEnabled` in its app scope. Games never receive that repository.
- [ ] Keep `MiniAppAudioEngine` public because `:feature:root` is a separate host module. Keep its implementation, `PlatformAudioSink`, renderer, queues and diagnostics internal. Common tests inject a fake sink until Tasks 10–11 provide platform bindings.
- [ ] Inject `MiniAppAudioEngine` into `DefaultRootComponentFactory`, pass it to `MiniAppRuntimeCoordinator`, open the facade before `plugin.createSession`, and put it in the context.
- [ ] On plugin creation failure/cancellation, close the provisional audio session. On lifecycle destroy, close it before clearing active Root state. All closure is idempotent and session-key guarded.
- [ ] Drain diagnostic counters on a normal coroutine and report only repeated underruns, validation rejection, queue overflow, forced shedding and backend failures via `CrashlyticsRepository`. Do not report normal play/stop/control changes as exceptions.
- [ ] Run:

  ```bash
  ./gradlew :miniapp:audio:allTests :miniapp:compose:allTests \
    :miniapp:metro:allTests :feature:root:allTests
  ```

- [ ] Commit.

## Task 10: Implement the Android `AudioTrack` sink

**Files:**

- Create: `miniapp/audio/src/androidMain/kotlin/ge/yet/game/miniapp/audio/internal/AndroidAudioSink.kt`
- Create: `miniapp/audio/src/androidMain/kotlin/ge/yet/game/miniapp/audio/di/AndroidMiniAppAudioBindings.kt`
- Create: `miniapp/audio/src/androidHostTest/kotlin/ge/yet/game/miniapp/audio/internal/AndroidAudioSinkTest.kt`
- Modify: `miniapp/audio/build.gradle.kts`
- Modify: `composeApp/src/androidMain/kotlin/ge/yet/game/di/AndroidAppGraph.kt` only if Metro cannot aggregate the platform binding automatically.

- [ ] First add a compile/API RED for the sink and a Robolectric-friendly configuration test. Do not pretend Robolectric proves realtime playback.
- [ ] Select a supported native sample rate and minimum stereo streaming buffer with `AudioTrack.getMinBufferSize`; use PCM float when supported and a tested PCM16 conversion fallback otherwise.
- [ ] Own one dedicated high-priority writer thread/coroutine boundary. The audio rendering callback writes only preallocated buffers and catches all platform exceptions into counters.
- [ ] Implement start, pause, resume, flush/release, audio-focus loss/duck/gain and recovery without reconstructing contributor programs.
- [ ] Verify teardown is idempotent and no thread survives app graph disposal in the test harness.
- [ ] Run:

  ```bash
  ./gradlew :miniapp:audio:testAndroidHostTest \
    :miniapp:audio:compileAndroidMain :composeApp:compileAndroidMain \
    :androidApp:assembleDebug
  ```

- [ ] Record a manual physical Android checklist: audible Music/SFX, setting gates, background/foreground, focus interruption, no release click, underrun counters. Do not mark the device items verified unless actually run.
- [ ] Commit.

## Task 11: Implement the iOS `AVAudioEngine` sink

**Files:**

- Create: `miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/internal/IosAudioSink.kt`
- Create: `miniapp/audio/src/iosMain/kotlin/ge/yet/game/miniapp/audio/di/IosMiniAppAudioBindings.kt`
- Create: `miniapp/audio/src/iosTest/kotlin/ge/yet/game/miniapp/audio/internal/IosAudioSinkTest.kt`
- Modify: `miniapp/audio/build.gradle.kts`
- Modify: `composeApp/src/iosMain/kotlin/ge/yet/game/di/NativeAppGraph.kt` only if Metro cannot aggregate the platform binding automatically.

- [ ] Create a narrow compile spike first to confirm Kotlin/Native signatures for `AVAudioSourceNode` on the checked-in toolchain. Capture API mismatch as RED before expanding implementation.
- [ ] Configure `AVAudioSession` for game playback/mixing policy, create one app-owned `AVAudioEngine`, attach/connect an `AVAudioSourceNode`, and copy rendered stereo frames into the provided `AudioBufferList` without allocation or escaped exceptions.
- [ ] Handle interruption begin/end, route changes, media-service reset, background pause/resume and idempotent teardown through non-realtime command paths.
- [ ] Native tests cover configuration/state transitions and an injected fake renderer. Simulator compilation is necessary but not evidence of audible or low-latency output.
- [ ] Run:

  ```bash
  ./gradlew :miniapp:audio:iosSimulatorArm64Test \
    :miniapp:audio:compileKotlinIosSimulatorArm64 \
    :composeApp:linkDebugFrameworkIosSimulatorArm64
  ```

- [ ] Record a manual iPhone checklist matching Android plus route/phone-call interruptions. Leave it explicitly unverified if no device is available.
- [ ] Commit.

## Task 12: Make presets contributor-default and prove boundaries in build logic

**Files:**

- Modify: `build-logic/convention/src/main/kotlin/com/yet/plugins/miniapp/MiniAppConventionPlugin.kt`
- Modify: `build-logic/convention/src/main/kotlin/com/yet/plugins/miniapp/MiniAppDependencyBoundary.kt`
- Modify: `build-logic/convention/src/test/kotlin/com/yet/plugins/miniapp/MiniAppConventionPluginTest.kt`
- Modify: `build-logic/convention/src/test/kotlin/com/yet/plugins/miniapp/MiniAppDependencyBoundaryTest.kt`
- Modify: `build-logic/convention/src/test/kotlin/com/yet/plugins/miniapp/CreateMiniAppTaskTest.kt`
- Modify: generated scaffold templates under `build-logic/convention/src/main/kotlin/com/yet/plugins/miniapp/` as located by the existing renderer tests.

- [ ] RED-test that `logica.miniapp` supplies `:miniapp:audio-presets` to commonMain, while direct Android/iOS audio libraries and raw platform audio imports remain forbidden for game modules.
- [ ] Add `implementation(project(":miniapp:audio-presets"))` in the convention. Keep the existing single `api(:miniapp:metro)` framework edge.
- [ ] Update generated scaffold contract tests so `context.audio` compiles and a shared SFX preset can be referenced without extra dependency boilerplate. Do not autoplay audio in generated sample code.
- [ ] Add boundary diagnostics for direct external/platform audio dependencies, without rejecting `:miniapp:audio` or `:miniapp:audio-presets` supplied by convention.
- [ ] Run the full build-logic suite under strict configuration cache and compile a generated game on Android+iOS.
- [ ] Commit.

## Task 13: Prove procedural audio in Counter and Block Blast

**Files:**

- Modify: `miniapp/samples/counter/src/commonMain/kotlin/ge/yet/sample/counter/...`
- Modify: `miniapp/samples/counter/src/commonTest/kotlin/ge/yet/sample/counter/...`
- Modify: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/data/audio/...`
- Modify: Block Blast Store/component/session bindings and their tests

- [ ] Extend the existing unshipped Counter reference instead of adding a second sample MiniApp. Its UI demonstrates Play/Stop Music, four original SFX triggers and an `intensity` control using `OceanBreeze`, with no audio asset.
- [ ] Inject `MiniAppAudio` through Counter's existing session graph and test accepted commands, control updates, lifecycle ownership and session isolation with a recording facade.
- [ ] Migrate Block Blast gameplay from filename/playlist commands to a game-semantic `BlockBlastAudioPlayer` backed by the session's `MiniAppAudio`.
- [ ] Give Block Blast a game-owned procedural program, shared soundscape/preset reuse and a typed SFX mapping for every `FeedbackType`; keep DSP and host policy out of the Store.
- [ ] Run Counter and Block Blast tests/Android+iOS compilation, integration tests, bundle verification and the production app build.
- [ ] Commit.

## Task 14: Write author documentation and the game-audio skill

**Files:**

- Create: `docs/miniapp/audio/getting-started.md`
- Create: `docs/miniapp/audio/kotlin-dsl.md`
- Create: `docs/miniapp/audio/instruments.md`
- Create: `docs/miniapp/audio/patterns.md`
- Create: `docs/miniapp/audio/effects.md`
- Create: `docs/miniapp/audio/adaptive-music.md`
- Create: `docs/miniapp/audio/sfx-recipes.md`
- Create: `docs/miniapp/audio/performance-budgets.md`
- Create: `docs/miniapp/audio/troubleshooting.md`
- Create: `.agents/skills/miniapp-procedural-audio/SKILL.md`
- Create: `.agents/skills/miniapp-procedural-audio/references/public-api.md`
- Create: `.agents/skills/miniapp-procedural-audio/references/music-recipes.md`
- Create: `.agents/skills/miniapp-procedural-audio/references/sfx-recipes.md`
- Create: `.agents/skills/miniapp-procedural-audio/references/review-checklist.md`
- Modify: `README.md`
- Modify: `AGENTS.md`

- [x] Before writing the skill, create realistic authoring prompts and verify the existing repository guidance does not already answer them reliably. This is the skill's RED; no subagent is required.
- [x] Document only public author behavior. Link to engine-maintainer design rather than teaching game authors to change DSP/backend internals.
- [x] Make the skill trigger on creating, changing, reviewing or diagnosing procedural Music/SFX in a game. It must enforce this sequence: reuse preset → adjust controls/seed/gain/density/stereo → compose presets → create game-owned declaration only if necessary.
- [x] Include compact, original examples for menu ambience, adaptive intensity, placement/collision/success SFX, deterministic randomness and teardown. Do not include or paraphrase a third-party composition.
- [x] Explain that 8/16/32/64-bit aesthetics are bit-crush/sample-rate effects; the platform stream remains high-quality PCM.
- [x] Compile every documentation snippet in a dedicated commonTest fixture or generated sample test.
- [x] Validate the skill with the repository skill validator (`quick_validate.py` from the installed skill-creator package) and manually apply its checklist to Counter and Block Blast.
- [x] Add README/AGENTS links to the author docs, skill, architecture design and this implementation plan.
- [x] Commit.

## Task 15: Final integration, realtime audit and release proof

The iOS absolute realtime boundary is implemented and verified through
`docs/superpowers/plans/2026-08-24-ios-realtime-pcm-producer.md`.

**Files:**

- Modify only files required by failures found in this task.
- Update: `AGENTS.md` verification commands and module table if the final implementation differs from the plan.

- [x] Run source scans proving realtime code contains no `launch`, suspension, logging, storage, DI, string formatting, mutable collection growth or contributor callback invocation.
- [x] Run dependency reports proving:
  - `core:pattern` has no project edges;
  - `audio-presets` depends only on `audio`;
  - game modules do not depend directly on platform audio;
  - Counter remains absent from `miniapp:bundle` and the production APK;
  - Block Blast uses the session-scoped procedural facade rather than direct platform audio.
- [x] Run the full gate:

  ```bash
  ./gradlew \
    :core:pattern:allTests \
    :miniapp:audio:allTests \
    :miniapp:audio-presets:allTests \
    :miniapp:samples:counter:allTests \
    :game:blockblast:allTests \
    :miniapp:integration-test:allTests \
    :feature:root:allTests \
    :composeApp:allTests \
    :miniapp:bundle:verifyMiniAppBundle \
    :composeApp:compileAndroidMain \
    :androidApp:assembleDebug \
    :composeApp:linkDebugFrameworkIosSimulatorArm64 \
    --rerun-tasks
  ```

- [x] Run the convention tests and strict configuration-cache reuse gate.
- [x] Run deterministic offline renders twice on Android/JVM and iOS simulator; compare quantized hashes where exact and acoustic tolerances where floating-point variance is allowed.
- [ ] Execute physical Android/iPhone audio smoke checks if devices are available. Report unverified device behavior explicitly; simulator/framework success is not a substitute.
  2026-08-24: the paired iPhone 13 Pro and Apple Watch were offline; no
  Android device/`adb` executable was available. The physical audible-output
  and Instruments allocation checks remain manual.
- [x] Refresh the codebase-memory index after the structural change.
- [x] Review the full branch diff for accidental third-party code/text, public API leakage, dependency cycles, stale-session holes and misleading documentation.
- [x] Commit any final scoped fixes, then hand off exact commands, test counts, manual gaps and commit SHAs.

## Definition of done

- A game can author original procedural Music/SFX in Kotlin with no MP3 and no Settings/lifecycle/platform boilerplate.
- Shared presets are available automatically to scaffolded games but remain optional at runtime.
- Android and iOS use the same compiled program, scheduler and DSP implementation through separate sinks.
- A destroyed/replaced MiniApp cannot affect current audio.
- Music/SFX settings and visibility policies are host-owned and independently enforced.
- Realtime paths satisfy the no-allocation/no-blocking/no-logging boundary by inspection and tests.
- Counter proves the complete authoring surface without shipping, while Block Blast proves the same API in a production game.
- Documentation and the repo skill teach game authorship, not engine modification.
- No copied Klang/Strudel code, preset or composition is present.
