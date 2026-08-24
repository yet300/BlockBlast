# Original SFX recipes

## Reuse first

- Placement/tap: `PlacementClick`
- Success/completion: `SuccessSweep`
- Impact/destruction: `Explosion`
- Charge/reward: `PowerUp`

Rename by domain role and tune gain/seed before writing a voice.

## Game-owned fallback

Placement: short transient, fast decay, low gain, safe under rapid repetition.

Collision: pitched low oscillator plus seeded noise, short filter envelope, restrained distortion.

Success: upward pitch motion with longer release, but short enough for repeated navigation.

Keep every declaration original. Do not reproduce recognizable samples, melodies, or third-party demo parameter sets. Verify the SFX over active music and under rapid repeated triggering.

See `docs/miniapp/audio/sfx-recipes.md` for the compiled collision example.
