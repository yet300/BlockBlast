# Voice Feedback Selector — Implementation Plan

**Goal:** Define one deterministic domain decision for the optional voice phrase produced by a resolved move.

## Scope

- Add the five supported voice responses as a domain enum.
- Add a pure selector implementing the approved priority order.
- Cover priority collisions and silent moves with focused unit tests.
- Do not change audio playback or `GameEngine` event emission in this stage.

## TDD Steps

- [x] Add selector tests for every response and silent cases.
- [x] Run `:core:domain:allTests` and verify RED because the selector API is absent.
- [x] Add the minimum enum and selector implementation.
- [x] Run `:core:domain:allTests` and verify GREEN.
- [x] Run `git diff --check` and present the uncommitted stage for review.
