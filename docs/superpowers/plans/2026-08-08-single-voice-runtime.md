# Single Voice Runtime — Implementation Plan

**Goal:** Play at most one domain-selected voice per move and remove every automatic fallback to `Amazing`.

## Root Cause

- `GameEngine` emits `ComboActive` for every combo level from 2 onward.
- `GameStoreFactory` turns every `ComboActive` into `playVoiceCombo(level)`.
- Android and iOS request nonexistent `voice_combo_N` assets and fall back to `voice_amazing`.
- A move can also emit `Feedback`, creating a second independent voice request.

## Scope

- Add one `GameEvent.VoiceFeedback(FeedbackType)` selected by the domain priority function.
- Use the existing `FeedbackType` as the single five-value feedback catalog.
- Keep `Feedback` and `ComboActive` as visual/analytics events only.
- Replace the audio API with `playVoiceFeedback(VoiceFeedback)` and remove `playVoiceCombo`.
- Remove combo filename lookup and every fallback to `Amazing` on Android and iOS.
- Stop the previous voice stream before starting an accepted new voice.
- Do not change placement/clear resource names in this stage.

## TDD Steps

- [x] Add a domain test proving combo 3 emits exactly one `Amazing` and combo 4 does not repeat it.
- [x] Add a store test proving only the selected voice reaches `AudioRepository`.
- [x] Run focused tests and verify RED because the voice event/API does not exist.
- [x] Wire domain event, store handling, repository, and platform players.
- [x] Remove `playVoiceCombo` from production and test doubles.
- [x] Run domain, data, game, and root tests.
- [x] Compile Android and link the iOS simulator framework.
- [x] Run `git diff --check` and present the uncommitted stage for review.
