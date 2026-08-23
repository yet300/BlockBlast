# Adaptive music

Declare the musical structure once and expose a few semantic controls. In the component, translate game state into those controls only when the value materially changes.

The `MenuAudio` example in [getting started](getting-started.md) maps one `intensity` control to wind, water, waves and chimes. That is preferable to switching between several near-identical programs: the program stays immutable while the renderer changes parameters smoothly.

Recommended mapping process:

1. Define the game meaning and legal range, usually `0f..1f`.
2. Give it a calm default that matches initial state.
3. Map it to conservative per-layer ranges.
4. Clamp/normalize game state before calling `setControl`.
5. Treat `CONTROL_OUT_OF_RANGE` as a caller bug, not a recoverable playback event.

Use a stable seed for procedural variation. A seed changes the composition's deterministic identity; a control changes its live expression. Do not generate a new seed each frame or each recomposition.

Visibility, global Music/SFX settings and session teardown are host-owned. An active program may be suppressed while hidden and resume according to the runtime policy; a game must not observe the app lifecycle to manipulate a native player.
