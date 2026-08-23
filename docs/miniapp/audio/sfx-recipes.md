# SFX recipes

Prefer the shared original presets:

- placement/tap: `PlacementClick`
- positive completion: `SuccessSweep`
- impact/destruction: `Explosion(seed = ...)`
- charge/reward: `PowerUp`

Rename them to domain meanings and keep typed `SfxName` values beside the program. Use one program for related music and SFX when practical.

Only create a game-owned declaration when adjusting preset `gain`, `seed`, or name cannot express the required role:

```kotlin
private val originalCollisionProgram = audioProgram {
    sfx("collision") {
        noise(NoiseColor.BROWN, gain = 0.32f, seed = 17L)
        oscillator(OscillatorShape.SINE, gain = 0.16f)
        pitch(from = 110.hz, to = 48.hz, duration = 180.ms)
        envelope(attack = 1.ms, decay = 50.ms, sustain = 0.2f, release = 220.ms)
        lowPass(cutoff = 900.hz, resonance = 0.2f)
        distortion(amount = 0.12f)
    }
}
```

Placement should be short and quiet enough for rapid repetition. Collision may combine a low pitch sweep and noise but must retain headroom. Success can be longer and brighter, while still allowing rapid navigation away without owning teardown. For variants, change an explicit deterministic seed; do not add random platform calls inside input handling.

Always audition repeated triggering and simultaneous music. A sound that is safe once can clip or become tiring at gameplay rate.
