# Troubleshooting procedural audio

## No sound

Inspect `AudioCommandResult` first. `PLAYBACK_SUPPRESSED` means visibility or user settings intentionally mute the command. `BACKEND_UNAVAILABLE` means the platform sink could not start. `SESSION_CLOSED` means the component retained an obsolete audio handle. Do not hide these results behind `runCatching`.

Confirm that the command uses the session's injected `MiniAppAudio`, not a fabricated implementation, and that `playSfx` receives the same program containing the requested typed `SfxName`.

## Invalid program or unknown name

Read diagnostics on `AudioCommandResult.Rejected`. Names must be lowercase snake case. Included fragments must have unique declaration names, instruments must exist before track resolution, and controls used by parameters must be declared.

## Control rejected

Use the range declared by `control`. Normalize and clamp domain state before `setControl`; do not silently widen a carefully tuned audio range.

## Audio is harsh, clips, or costs too much

Reduce gains and overlapping sources first. Then shorten release/delay tails, reduce feedback, remove redundant layers, lower effect amounts, and inspect [mobile budgets](performance-budgets.md). Test rapid SFX repetition over active music.

## Android and iOS differ

Use deterministic offline render tests to separate declaration/DSP problems from platform sink problems. Then run the platform verification in `docs/verification/miniapp-audio-android.md` or `miniapp-audio-ios.md`. Do not add platform code to a game module.

## Music survives the wrong game screen

Call `stopMusic` for an intentional in-session pause. Leaving the MiniApp destroys its session and closes audio automatically. If audio survives session destruction, it is an engine/host defect; use the maintainer design and plan rather than adding lifecycle workarounds to the game.
