# Mobile audio budgets

The compiler rejects programs beyond the mobile safety envelope. Author below these ceilings rather than treating them as targets.

| Resource | Maximum |
|---|---:|
| Simultaneous voices | 32 |
| Voices reserved for SFX | 8 |
| Music tracks | 16 |
| Oscillators per instrument or SFX | 8 |
| Noise sources per voice | 4 |
| Additive partials per voice | 32 |
| Filters per voice | 4 |
| Voice effects per voice | 4 |
| Send effects per target | 4 |
| Delay time | 4 seconds |
| Delay feedback | 0.95 |
| Nested audio-parameter depth | 8 |
| Pattern operations | 4,096 |
| Pattern events | 256 |

Practical author budget: begin with one to four music tracks, one or two sources per voice, short tails, and at least 6 dB of subjective headroom. Avoid inaudible layers and redundant oscillators. Reuse a compiled immutable program rather than rebuilding it.

Run `./gradlew :your:module:allTests` and add an acoustic render test when authoring a new voice or preset. Assert deterministic hash for a fixed seed, finite samples, peak below `1f`, a useful RMS range, and the intended stereo/control response. The experimental `MiniAppAudioTestRenderer` is test-only: its typed request renders music, control overrides, and frame-offset SFX triggers through the same bounded realtime mixer used by platform sinks. Requests reject unknown names, duplicate or invalid controls, out-of-range offsets, and oversized collections; returned PCM channels are defensive copies. Keep higher-level analysis such as spectral centroid and pitch direction local to the consuming test.
