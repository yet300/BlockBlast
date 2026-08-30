# Fruit Merge

Fruit Merge is an original, deterministic drop-and-merge MiniApp for Logica. Tap anywhere in the
game viewport to drop the preview fruit, or drag horizontally and release to choose its position.
Equal fruit combine into the next level. Keep every fruit below the danger line; a continuous
1.5-second overflow ends the run. Accepted drops have a 450 ms cooldown so fruit cannot be spammed
into an unresolved airborne stack.

Each new run includes five clears and three shakes. After those free actions are spent, the same
action passes through the host interstitial gate. When advertising is disabled or unavailable, the
host completes the gate immediately. Clear removes one selected fruit; shake applies bounded,
deterministic impulses and then lets ordinary collision rules resolve merges.

The regular play surface is icon-first: Bomb starts clear targeting, Vibration shakes the pile, and
compact badges communicate remaining free uses or the advertising gate. A persisted two-step,
pass-through tutorial demonstrates tap and drag without blocking the board. Overflow navigates to a
dedicated Decompose Result destination with the score, best score, largest fruit, and one New game
action. The supporting pane shows the complete ten-fruit evolution chain.

The viewport uses the shared `AdaptiveGameScaffold`: compact windows place the board above a
scrollable action panel, while wide and compact-height windows use two panes. The board and all
fruit are rendered by one Canvas. Physics advances at a deterministic 60 Hz with a three-step
frame cap, a bounded spatial grid, at most 80 bodies, and no per-fruit coroutine or Compose node.
Reduced-motion settings disable optional blinking and targeting pulses without stopping gameplay
physics; the tutorial uses a static gesture cue and omits its completion burst.

Fruit art is original runtime vector drawing: every level has a matte orchard color, clay-like
highlight, distinct silhouette and markings, leaf, blush, and a face that blinks or reacts to
impacts and danger. The catalog icon is an original vector in the same visual language.

Music and SFX are original procedural declarations rendered through the session-bound MiniApp audio
API. The 84 BPM music evokes a hand-shaken fruit crate using wooden knocks, rolling noise, sparse
glass-like accents, and seeded stereo movement. Drop, merge tiers, clear, shake, and game-over each
have a validated deterministic SFX program; no recorded or third-party audio asset is bundled.

This module does not own production shipping authorization. The maintainer-owned `miniApps`
allowlist in `settings.gradle.kts` is the sole shipping source. Verify the module independently with:

```bash
./gradlew :game:fruitmerge:allTests :game:fruitmerge:validateMiniAppDependencies
./gradlew :game:fruitmerge:compileAndroidMain :game:fruitmerge:compileKotlinIosSimulatorArm64
./gradlew :game:fruitmerge:verifyMiniApp
```

See `submission.json` and `PROVENANCE.md` for the acceptance contract and source record.
