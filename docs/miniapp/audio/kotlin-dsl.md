# Kotlin audio DSL

`audioProgram {}` produces an immutable declaration. Build it once in an `object` or stable property; never rebuild it on recomposition or on every input event.

## Program structure

- `tempo(20f..400f)` sets BPM.
- `control(name, default, range)` declares a runtime control and returns a reference. Map it into an output range with `reference.map(min, max)`.
- `include(fragment)` composes reusable declarations.
- `instrument`, `musicTrack`, and `sfx` define game-owned audio when presets cannot express the result.
- `musicBus` and `sfxBus` apply delay/reverb to their respective buses.

Names must be lowercase snake case and start with a letter. Keep typed `AudioControlName` and `SfxName` constants next to the program to prevent command-site string drift.

## Voices and tracks

An instrument or SFX voice can combine oscillators (`SINE`, `TRIANGLE`, `SAW`, `SQUARE`, `PULSE`), `WHITE`/`PINK`/`BROWN` noise, additive partials, FM, vibrato, filters, an ADSR envelope, distortion and bit crushing. A music track selects an instrument, supplies notes or a `Pattern<AudioNote>`, then configures gain, pan, delay and reverb.

Gain is not a loudness target. Keep preset/program gain conservative, render-test peaks, and leave headroom for overlapping SFX.

## Parameters

The supported modulators are constants, mapped controls, `sineLfo`, deterministic `smoothNoise`, and multiplication with `times`. Parameter output ranges must remain valid for their destination. Prefer a small number of semantic controls such as `intensity`, `danger`, or `speed`; UI-specific state does not belong in the audio program.

## Runtime commands

`MiniAppAudio` exposes only `playMusic`, `stopMusic`, `playSfx`, and `setControl`. Music is the current session program; starting another program replaces it. SFX names are looked up in the program supplied to `playSfx`.

The public author API is summarized in the repo skill reference: [public-api.md](../../../.agents/skills/miniapp-procedural-audio/references/public-api.md).
