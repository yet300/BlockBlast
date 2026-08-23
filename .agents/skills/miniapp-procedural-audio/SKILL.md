---
name: miniapp-procedural-audio
description: Use when creating, changing, reviewing, or diagnosing procedural Music or SFX inside a MiniApp or game module.
---

# MiniApp Procedural Audio

Author original Kotlin audio declarations through the public MiniApp API. This skill does not cover changing DSP, platform sinks, the audio engine, or host lifecycle policy.

## Read first

1. Read [public-api.md](references/public-api.md) for declarations, commands, naming and lifecycle.
2. For music, read [music-recipes.md](references/music-recipes.md).
3. For SFX, read [sfx-recipes.md](references/sfx-recipes.md).
4. Before completion, apply [review-checklist.md](references/review-checklist.md).

## Required authoring order

1. Reuse an original preset from `:miniapp:audio-presets`.
2. Adjust only its public controls: name, seed, gain, density and stereo/layer parameters as available.
3. Compose multiple renamed presets if one is insufficient.
4. Add a game-owned declaration only when the required sonic role still cannot be expressed.

Do not copy Klang, Strudel, a commercial song, another game's soundtrack, or a third-party demo composition. Concepts such as sequencing, synthesis and filters are generic; notes, rhythm, orchestration, seeds and parameter choices must be original.

## Workflow

- Keep one immutable program in game-owned non-UI code.
- Keep `AudioControlName` and `SfxName` constants beside it.
- Inject the session-bound `MiniAppAudio` into a component/state holder.
- Translate game events into `playMusic`, `stopMusic`, `playSfx`, and infrequent `setControl` calls.
- Inspect `AudioCommandResult`; never retry a rejected command in a loop.
- Let host visibility/settings/session teardown control suppression and closure.
- Add a common test that compiles the declaration. For new voices/presets, use deterministic offline rendering and acoustic assertions.
- Run the module's `allTests` plus Android/iOS compilation.

For an 8/16-bit aesthetic use oscillator/envelope constraints and `bitCrush`; do not reduce the platform output format. The engine renders high-quality PCM.

If the request requires a missing author capability, stop at a concrete API gap and point the maintainer to `docs/superpowers/specs/2026-08-23-kotlin-pattern-audio-design.md` and the implementation plan. Do not reach into `internal` packages from a MiniApp.
