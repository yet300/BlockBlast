# Kotlin Pattern and Procedural Audio Design

**Status:** Approved design

## Goal

Provide a reusable Kotlin Multiplatform pattern engine and a host-managed
procedural-audio capability that any future game or MiniApp can use without
shipping an MP3. The temporal pattern layer must also be reusable for visuals,
enemy waves, haptics and other event streams.

The conceptual model is inspired by Strudel's documented pattern/query model,
not by copying its implementation. Strudel describes patterns as values queried
over time arcs, with schedulers repeatedly querying future windows and outputs
interpreting the resulting events. See the official
[Patterns documentation](https://strudel.cc/technical-manual/patterns/) and
[technical manual](https://github.com/tidalcycles/strudel/wiki/Technical-Manual).
Strudel's implementation is AGPL-3.0, so Logica will use a clean-room native
Kotlin implementation and its own API, tests and terminology where practical.

## Module Split

### `:core:pattern`

A pure, platform-neutral and audio-independent engine:

- exact rational cycle time rather than accumulated floating-point clock time;
- `Pattern<T>` queried with a `TimeArc` to produce `PatternEvent<T>` values;
- immutable, serializable `PatternDocument` AST;
- sequence, stack, repeat, shift, slow, fast, every, choose and degrade
  combinators;
- deterministic seeded randomness;
- bounded query evaluation with no Compose, DI, coroutines or audio APIs.

### `:miniapp:audio`

The MiniApp-facing musical vocabulary and session capability:

- note and frequency events;
- oscillator and bounded noise sources;
- ADSR envelopes, gain, pan and filters;
- bounded delay and reverb;
- tempo and named runtime controls such as `intensity`;
- optional approved sample events for original/licensed assets;
- lifecycle-aware play, stop and control APIs.

### Platform Runtime

- a look-ahead scheduler queries pattern windows;
- a PCM renderer mixes bounded voices;
- Android uses `AudioTrack`;
- iOS uses `AVAudioEngine`;
- playback follows session lifecycle, visibility and host audio preferences;
- destroying the child session cancels scheduler and renderer work exactly
  once.

## Canonical Program Format

The canonical interchange format is a versioned `PatternDocument` serialized
as JSON. A Kotlin DSL is a typed builder that produces the same AST:

```kotlin
val soundtrack = audioProgram {
    tempo(cyclesPerMinute = 120)
    control("intensity", default = 0.3f)
    track("bass") {
        notes("c2 ~ c2 eb2")
            .sound(Oscillator.Square)
            .gain(0.3)
            .cutoff(control("intensity").range(300f, 1_400f))
            .slow(2)
    }
}

context.audio.play(soundtrack)
context.audio.controls.set("intensity", 0.85f)
```

Human contributors may author the Kotlin DSL. The future website and background
AI pipeline generate validated JSON AST, not arbitrary Kotlin that executes on
the server or in the app.

## Session Capability

`MiniAppSessionContext` gains a typed `audio: MiniAppAudio` capability. Games
do not depend on `AudioTrack`, `AVAudioEngine`, an app-global file provider or a
platform player. The capability is already bound to the active MiniApp/session,
so ownership, cancellation and telemetry cannot collide between games.

The host applies global music/SFX preferences automatically. MiniApps express
semantic channels or buses; they do not reimplement Settings integration.

## Validation and Resource Budgets

Every document is validated before playback and before a generated PR can pass:

- supported schema version and node allowlist;
- maximum AST depth and serialized size;
- maximum events per cycle and simultaneous voices;
- valid tempo, duration, frequency, gain and pan ranges;
- bounded filter parameters and effect feedback;
- deterministic seed requirements;
- per-query operation budget;
- renderer CPU/underrun and clipping thresholds.

Invalid programs return structured diagnostics with a document path. They never
partially start playback.

## Procedural and Sample Audio

Procedural synthesis is the default because it is compact, adaptive and easy
to parameterize. Optional samples remain necessary for voice, foley or a sound
whose identity cannot be synthesized economically. Every sample requires
provenance, author, source and license metadata, and its license must permit the
repository and release distribution.

“8-bit” or “16-bit” is normally an aesthetic, not the PCM storage width. The
runtime can emulate quantization/sample-rate reduction as an effect while using
an implementation format such as 16-bit integer or 32-bit float PCM internally.

## Failure Semantics

- Invalid AST: reject before playback with validation errors.
- Unsupported optional node: reject the program version, never silently alter
  the music.
- Runtime overload: shed voices deterministically within declared priority,
  record diagnostics and avoid blocking UI threads.
- Visibility becomes obscured/inactive: apply the host policy (pause, duck or
  stop) without contributor code.
- Session destruction: cancel queries, audio writes and control collectors.
- Audio backend unavailable: expose a silent, diagnosed failure rather than
  crash the MiniApp.

## Verification

Use golden query tests for rational timing and combinators, property tests for
determinism/bounds, JSON compatibility tests, validator adversarial cases,
scheduler clock tests, clipping/polyphony tests and platform runtime tests.
Counter or another unshipped sample must demonstrate a non-audio
`Pattern<T>` use so the core cannot accidentally become music-specific.

## Non-Goals

- Source compatibility with Strudel JavaScript.
- Executing user-supplied Kotlin or JavaScript at runtime.
- A full DAW, tracker editor or network music service.
- Eliminating support for all audio assets.
