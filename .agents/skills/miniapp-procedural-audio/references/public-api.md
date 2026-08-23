# Public author API

## Construction

- `audioProgram { tempo; control; include; instrument; musicTrack; sfx; musicBus; sfxBus }`
- `audioProgramFragment { ... }` for reusable declarations
- Values: `Int.ms`, `Double.seconds`, `Int.hz`, `Double.hz`, `MidiNote.of(0..127)`
- Typed command names: `AudioControlName`, `SfxName`
- Names use lowercase snake case and begin with a letter.

## Voice sources and shaping

- Oscillators: sine, triangle, saw, square, pulse
- Noise: white, pink, brown with explicit `Long` seed
- Additive `partial`, frequency modulation, vibrato
- ADSR `envelope`
- Low/high/band-pass filters
- Distortion and bit crush
- Delay and reverb sends

Parameters are constants, mapped controls, sine LFO, seeded smooth noise, or a product. Keep every output range legal for its destination.

## Patterns

Use `pure`, `sequence`, `stack`, `euclidean`, `choose`, `degrade`, `fast`, `slow`, `shift`, `repeat`, and `every`. Randomized operations require explicit seeds and are deterministic.

## Runtime

`MiniAppAudio` has four commands: `playMusic`, `stopMusic`, `playSfx`, `setControl`. Handle `Accepted` or `Rejected(reason, diagnostics)`. Visibility, user Music/SFX preferences, session closure and backend availability can reject/suppress playback.

The handle belongs to one MiniApp session. The host closes it on lifecycle destruction. A game calls `stopMusic` only for its own intentional pause/transition.

Full author documentation: `docs/miniapp/audio/getting-started.md`.
