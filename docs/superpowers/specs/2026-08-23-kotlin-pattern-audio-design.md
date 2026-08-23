# Kotlin Pattern and Procedural Audio Design

**Status:** Approved design

## Goal

Provide a reusable Kotlin Multiplatform pattern engine and a host-managed
procedural-audio capability that future games and MiniApps can use without
shipping an MP3. The pattern layer must remain useful for visuals, enemy waves,
haptics and other temporal event streams rather than becoming music-specific.

The first release accepts only audio programs compiled from ordinary Kotlin
source in the MiniApp module. It does not parse or execute Kotlin, JavaScript,
JSON programs or downloaded code at runtime. A future contribution website may
generate a `.kt` file and open a pull request, but the reviewed source is still
compiled into the application through the normal shipping allowlist.

## Clean-Room Boundary

The architecture is informed by public descriptions of query-based pattern
systems and modular synthesizers, including Strudel and Klangmotör. Klang places
most of its DSP, voices, scheduler and musical contracts in Kotlin `commonMain`,
then connects that code to JVM Java Sound and browser Web Audio backends. It has
no Android AudioTrack or iOS AVAudioEngine backend at the time of this design.

Klang and Strudel are AGPL-3.0 works. Logica must not copy, translate, adapt or
vendor their production source, DSL declarations, tests, presets or songs. The
engine is an independent implementation using its own API, terminology, test
vectors and code. General audio engineering ideas, public specifications,
standard music theory and established DSP mathematics may inform the design.

References:

- [Klangmotör repository](https://github.com/PeekAndPoke/klang)
- [Klangmotör license](https://github.com/PeekAndPoke/klang/blob/main/LICENSE)
- [Strudel patterns documentation](https://strudel.cc/technical-manual/patterns/)

Source compatibility with either project is explicitly not a goal.

## Module Split

### `:core:pattern`

A pure, audio-independent temporal engine:

- exact rational cycle time rather than accumulated floating-point clock time;
- `Pattern<T>` queried with a half-open `TimeArc`;
- immutable `PatternEvent<T>` values;
- sequence, stack, repeat, shift, slow, fast, every, choose and degrade;
- Euclidean rhythm and deterministic seeded randomness;
- bounded query evaluation;
- no Compose, DI, coroutines, audio or platform dependencies.

The public conceptual contract is:

```kotlin
interface Pattern<T> {
    fun query(arc: TimeArc): List<PatternEvent<T>>
}
```

Counter or another unshipped reference must demonstrate a non-audio pattern so
the module cannot accidentally become tied to notes or PCM rendering.

### `:miniapp:audio`

The public MiniApp surface contains:

- `AudioProgram` and its Kotlin builder DSL;
- notes, frequencies, instruments and oscillators;
- envelopes, filters, effects, gain and pan;
- Music and SFX buses;
- named, bounded runtime controls;
- the lifecycle-bound `MiniAppAudio` capability.

Its internal implementation contains:

- look-ahead scheduling;
- polyphonic voice allocation;
- the DSP graph, mixer and limiter;
- diagnostics and offline rendering;
- Android and iOS platform sinks.

MiniApps never receive PCM buffers, `AudioTrack`, `AVAudioEngine`, platform
players, app-global repositories or a callback that executes in the realtime
renderer.

### `:miniapp:audio-presets`

An optional library of original, reusable Kotlin declarations built only on
the public `:miniapp:audio` API. It contains parameterized instruments, SFX and
soundscapes, not PCM samples and not platform code. Initial families include:

- instruments such as SoftPad, ChipLead, AnalogBass and GlassBell;
- SFX such as PlacementClick, SuccessSweep, Explosion and PowerUp;
- soundscapes such as OceanBreeze, SoftRain, ForestNight and DeepSpace.

Presets are immutable factories with explicit controls, seed, gain, density
and stereo parameters. Games compose or parameterize them instead of copying
their implementation. The module cannot access internal DSP types, session
ownership, Settings or platform sinks.

## Public Authoring Model

The DSL builds an immutable declaration which is validated and compiled before
playback:

```text
Kotlin DSL -> AudioProgram -> validation -> CompiledAudioGraph -> renderer
```

Example:

```kotlin
internal val blockBlastAudio = audioProgram {
    tempo(bpm = 112)
    control("intensity", default = 0.25f, range = 0f..1f)

    instrument("bass") {
        oscillator(OscillatorShape.Saw, detuneCents = -4f)
        oscillator(OscillatorShape.Square, gain = 0.35f, detuneCents = 4f)
        envelope(attack = 8.ms, decay = 140.ms, sustain = 0.45f, release = 180.ms)
        lowPass {
            cutoffHz = control("intensity").map(450f, 2_400f)
            resonance = 0.25f
        }
    }

    musicTrack("bass") {
        instrument("bass")
        notes(C2, Rest, C2, Eb2, G2, Rest, Eb2, C2).slow(2)
    }

    sfx("place") {
        oscillator(OscillatorShape.Sine)
        pitch(from = 240.hz, to = 120.hz, duration = 55.ms)
        envelope(attack = 1.ms, release = 60.ms)
    }
}
```

Shared presets are included declaratively:

```kotlin
internal val calmSoundtrack = audioProgram {
    include(
        OceanBreeze(
            seed = 42,
            wind = control("wind", default = 0.45f),
            water = control("water", default = 0.6f),
            waves = control("waves", default = 0.7f),
            chimes = control("chimes", default = 0.15f),
        ),
    )
}
```

Games use the session capability rather than implementing Settings or lifecycle
integration:

```kotlin
context.audio.playMusic(blockBlastAudio)
context.audio.playSfx(blockBlastAudio.sfx("place"))
context.audio.setControl(AudioControlName("intensity"), 0.8f)
```

The public API is independent of the internal renderer so a future native C or
C++ DSP implementation would not require contributor modules to change.

## DSP Graph

The first engine supports:

- sine, triangle, saw, square and variable-width pulse oscillators;
- white, pink and brown deterministic seeded noise;
- multiple oscillators per instrument;
- local oscillator FM, pitch envelopes and LFO vibrato;
- sine LFO, smooth seeded value-noise modulation and stereo auto-pan;
- ADSR envelopes;
- low-pass, high-pass and resonant band-pass filtering;
- bounded additive partials for instruments such as bells;
- distortion and bit-crush/sample-rate-reduction effects;
- bounded delay/reverb sends and algorithmic reverb;
- scale-constrained deterministic note selection;
- gain, equal-power pan, bus mixing and a final limiter.

The fixed voice pipeline is:

```text
oscillator/noise
  -> pitch and FM modulation
  -> ADSR
  -> low/high/band-pass filter
  -> distortion/bit crush
  -> gain/pan
  -> Music or SFX bus
```

The bus pipeline is:

```text
voices -> bounded delay -> algorithmic reverb -> limiter -> master output
```

Free-form node graphs, arbitrary feedback, granular synthesis, convolution,
physical modelling and contributor-defined realtime DSP callbacks are deferred.
New built-in nodes can be added without changing existing programs.

`OceanBreeze` is an original acceptance preset for the shared layer. Its wind,
water and wave beds use independently seeded colored-noise layers, slow gain
and band-pass modulation, filter envelopes and stereo drift. Sparse chimes use
an additive glass-bell instrument constrained to an original pentatonic event
pattern. It must not translate or reproduce the structure, constants or
arrangement of a Klang, Sprudel, Strudel or other third-party composition.

The renderer uses stereo `Float` samples and the platform's actual sample rate,
normally 44.1 or 48 kHz. It renders blocks into preallocated buffers. An
“8-bit” aesthetic is produced by quantization and sample-rate reduction while
the engine retains a high-quality internal format.

## Music and SFX Buses

Music and SFX share one DSP implementation but have separate ownership and gain
policies:

- global Music settings affect only the Music bus;
- global SFX settings affect only the SFX bus;
- SFX receives reserved voices and is not delayed by the music scheduler;
- Music can loop, pause, duck and fade;
- SFX declarations have bounded maximum durations;
- gain changes are smoothed to avoid clicks.

## Session and Lifecycle Ownership

The application owns one realtime engine. Every active MiniApp receives an
isolated `MiniAppAudio` facade already bound to its `MiniAppId`, session key,
lifecycle, visibility and host audio preferences. Handles and commands from a
destroyed or replaced session are stale and cannot affect a later game.

Default visibility policy:

- `ACTIVE`: Music and SFX operate normally;
- `OBSCURED`: Music ducks smoothly and new SFX does not start;
- `INACTIVE`: the session scheduler and output pause;
- destroyed: commands are rejected, voices enter a bounded release and all
  remaining handles are then reclaimed.

The host automatically applies Music/SFX enabled state and volume, app
foreground/background, Android audio focus, iOS interruptions and route
changes. Games do not import Settings or reproduce this integration.

Runtime controls are declared in the program, validated against their ranges,
sent through a bounded command queue and smoothed inside the DSP. Updating a
control does not rebuild the graph.

## Realtime Thread Model

Game and UI code submits bounded commands such as Play, Stop, SetControl,
SetBusGain, VisibilityChanged and DestroySession. The realtime renderer consumes
a bounded batch and fills the next PCM block.

The realtime path must not:

- suspend or block;
- perform DI lookups;
- access Storage;
- log or create strings;
- allocate collections or resize buffers;
- invoke arbitrary contributor code.

Diagnostics are accumulated in preallocated counters and drained by an ordinary
application coroutine.

Android initially uses streaming `AudioTrack` from a dedicated audio thread.
iOS uses `AVAudioSession`, `AVAudioEngine` and `AVAudioSourceNode`. The shared DSP
does not depend on either platform API. AAudio/Oboe is an internal future option
only if device profiling proves `AudioTrack` insufficient.

## Validation and Budgets

Programs are rejected before playback if they contain unresolved references,
duplicate names, invalid values, cycles, unsupported nodes or work exceeding a
declared budget. Diagnostics contain a stable path such as:

```text
musicTrack[bass].instrument[analog-bass].filter[0].cutoffHz
```

Initial conservative mobile profile:

- at most 32 simultaneous voices, with at least 8 reserved for SFX;
- at most 16 tracks;
- at most 8 oscillators per instrument;
- at most 4 effects per track or bus;
- delay buffers no longer than 4 seconds;
- feedback no greater than `0.95`;
- at most 256 events per scheduler query window;
- bounded graph depth, query operations and command-queue capacity.

These are runtime profile values, not permanent public API guarantees. Device
benchmarks may safely raise them. When the voice limit is reached, stealing is
deterministic: released voices, then quietest, oldest and low-priority Music
voices. Reserved SFX capacity is preserved.

## Failure Semantics and Diagnostics

- Invalid programs return structured diagnostics and never partially start.
- Unknown instruments, SFX or controls return typed failures.
- Backend initialization failure produces diagnosed silence rather than a game
  crash.
- Intermediate SetControl commands may be coalesced when the command queue is
  saturated; Stop and Destroy are preserved.
- Overload sheds voices deterministically and records underruns.
- No exception may escape a native audio callback.

Outside the callback, repeated underruns, validation rejection, queue overflow,
forced voice shedding and backend failures are reported through the existing
`CrashlyticsRepository`. Normal playback, pause, stop and controls are not
exceptions.

## Documentation and Contributor Skill

Public usage documentation lives under:

```text
docs/miniapp/audio/
├── getting-started.md
├── kotlin-dsl.md
├── instruments.md
├── patterns.md
├── effects.md
├── adaptive-music.md
├── sfx-recipes.md
├── performance-budgets.md
└── troubleshooting.md
```

A repo-local `.agents/skills/miniapp-procedural-audio` skill activates only
when an agent creates, changes, reviews or diagnoses procedural Music or SFX
inside a game. It is not a guide for modifying the engine, platform sinks or
the library itself. Its detailed references cover the public DSL, original
music recipes, SFX recipes and a contributor review checklist. Examples teach
parameterized building blocks rather than providing songs to reproduce.

For game audio authoring, the skill follows this decision order:

1. reuse a suitable `:miniapp:audio-presets` declaration;
2. adjust only its published controls, seed, gain, density or stereo options;
3. compose multiple presets through the public DSL;
4. create a game-owned instrument or program only when the shared vocabulary is
   insufficient.

The skill does not instruct agents to modify shared preset internals. Changes
to the engine or shared preset library follow their maintainer documentation
and ordinary architecture review instead.

Library-maintainer documentation is separate from the contributor skill.

The existing unshipped `:miniapp:samples:counter` project is the executable
authoring reference. It demonstrates original procedural Music and SFX, a
runtime `intensity` control, visibility handling, session destruction,
Android/iOS aggregation and the absence of MP3 assets. Discovery must not add
it to the production allowlist. The shipped `:game:blockblast` plugin is the
production integration proof: gameplay addresses typed Music and SFX through a
game-owned semantic adapter without filenames or direct DSP dependencies.

## Verification

`:core:pattern` uses exact-boundary, combinator, deterministic-randomness,
property and adversarial query-budget tests.

`:miniapp:audio` uses oscillator-frequency, envelope, FM, filter-stability,
effect-tail, pan/gain, limiter, voice-stealing, bus-isolation, control-smoothing
and deterministic offline-render tests. Golden tests inspect frame count, peak,
RMS, dominant frequencies, silence regions and quantized PCM hashes. Operations
with allowed cross-platform floating-point variance use explicit tolerances.

`:miniapp:audio-presets` uses compile tests and golden acoustic-property tests
for every public preset. Soundscapes additionally prove deterministic seeds,
bounded event density, bounded tails, absence of clipping and stable control
response. Tests assert acoustic properties rather than preserving a third-party
waveform or arrangement.

Platform tests cover configuration, start/stop, focus or interruption,
preferences, route changes, teardown and backend recovery. Android device and
iPhone smoke tests remain mandatory because simulator/framework compilation
cannot prove latency, underrun or click-free output.

Documentation snippets compile in fixtures. The contributor convention rejects
direct platform audio libraries in MiniApp modules. The skill is validated with
the repository's skill validator and realistic audio-authoring scenarios. The
Counter acceptance tests prove visibility, controls and session-scoped audio
isolation without entering the production bundle. Block Blast tests prove typed
feedback routing, lifecycle teardown and production aggregation.

## Non-Goals

- Source or behavioral compatibility with Klang, Sprudel or Strudel.
- Runtime parsing or execution of Kotlin, JavaScript or downloaded programs.
- A live-coding editor, DAW, tracker or network music service.
- Replacing every sample asset.
- Arbitrary contributor DSP callbacks.
- Copying shared preset implementations into game modules.
- Shipping the Counter sample.
