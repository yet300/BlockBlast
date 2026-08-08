# Combo Grace Period — Implementation Plan

**Goal:** Preserve an active combo through two non-clearing placements and reset it on the third.

## Scope

- Persist `movesWithoutClear` in `GameState` with a legacy-safe default of `0`.
- On a clear: increment combo and reset misses to `0`.
- On misses one and two: preserve combo and increment misses.
- On miss three: reset combo and misses to `0`.
- Reset both values for a fresh game and rewarded revive.
- Do not change all-clear scoring, voice event wiring, or audio playback.

## TDD Steps

- [x] Replace the old immediate-reset test with grace-period transition tests.
- [x] Add reset assertions for new game, clear, and revive.
- [x] Run `:core:domain:allTests` and verify RED.
- [x] Add the minimum durable field and engine transition logic.
- [x] Verify old serialized saves default the new field to `0`.
- [x] Run `:core:domain:allTests` and verify GREEN.
- [x] Run `git diff --check` and present the uncommitted stage for review.
