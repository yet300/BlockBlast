# Fruit Merge

Fruit Merge is an original, deterministic drop-and-merge MiniApp for Logica. Position a preview
fruit, release it into the container, and combine equal fruit into the next level. Keep every fruit
below the danger line; a continuous 1.5-second overflow ends the run.

Each new run includes five clears and three shakes. After those free actions are spent, the same
action passes through the host interstitial gate. When advertising is disabled or unavailable, the
host completes the gate immediately. Clear removes one selected fruit; shake applies bounded,
deterministic impulses and then lets ordinary collision rules resolve merges.

The game is intentionally **not allowlisted**. Verify it independently with:

```bash
./gradlew :game:fruitmerge:verifyMiniApp
```

See `submission.json` and `PROVENANCE.md` for the acceptance contract and source record.
