# Fruit Merge

Fruit Merge is an original, deterministic drop-and-merge MiniApp for Logica. Position a preview
fruit, release it into the container, and combine equal fruit into the next level. Keep every fruit
below the danger line; a continuous 1.5-second overflow ends the run.

Each new run includes five clears and three shakes. After those free actions are spent, the same
action passes through the host interstitial gate. When advertising is disabled or unavailable, the
host completes the gate immediately. Clear removes one selected fruit; shake applies bounded,
deterministic impulses and then lets ordinary collision rules resolve merges.

The viewport uses the shared `AdaptiveGameScaffold`: compact windows place the board above a
scrollable action panel, while wide and compact-height windows use two panes. The board and all
fruit are rendered by one Canvas. Physics advances at a deterministic 60 Hz with a three-step
frame cap, a bounded spatial grid, at most 80 bodies, and no per-fruit coroutine or Compose node.
Reduced-motion settings disable optional blinking and targeting pulses without stopping gameplay
physics.

Fruit art is original runtime vector drawing: every level has a matte orchard color, clay-like
highlight, leaf, blush, and a face that blinks or reacts to impacts and danger. The catalog icon is
an original vector in the same visual language.

The game is intentionally **not allowlisted**. Verify it independently with:

```bash
./gradlew :game:fruitmerge:allTests :game:fruitmerge:validateMiniAppDependencies
./gradlew :game:fruitmerge:compileAndroidMain :game:fruitmerge:compileKotlinIosSimulatorArm64
```

See `submission.json` and `PROVENANCE.md` for the acceptance contract and source record.
