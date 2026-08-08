# All-Clear Bonus — Implementation Plan

**Goal:** Include the approved 300-point bonus when a clearing move leaves the board empty.

## Scope

- Add the existing `ScoreCalculator.allClearBonus` result to the move score.
- Include the bonus in best score, `lastPointsAwarded`, and `PiecePlaced` points.
- Preserve normal clear scoring when any cell remains.
- Do not change combo rules, feedback selection, or audio playback.

## TDD Steps

- [x] Add an engine test expecting `1 + 10 + 300 = 311` for a one-line all-clear.
- [x] Keep a separate one-line fixture non-empty and expecting `11`.
- [x] Run `:core:domain:allTests` and verify RED at `11 != 311`.
- [x] Add the minimum engine integration.
- [x] Run `:core:domain:allTests` and verify GREEN.
- [x] Run `git diff --check` and present the uncommitted stage for review.
