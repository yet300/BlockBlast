# Block Blast Bundled File Audio Implementation Plan

**Status:** Implemented

**Goal:** Restore Block Blast's existing bundled soundtrack and voice effects
without expanding the generic MiniApp API or duplicating native players.

## Completed work

- [x] Expose `musicEnabled` through the narrow feedback preference projection.
- [x] Model the exact Block Blast playlist and feedback-to-file mapping.
- [x] Keep Android/iOS file players, resource loading, settings policy, and app
  lifecycle handling in the existing `core:data` pipeline.
- [x] Make `AudioRepository` a synchronous command API while retaining its
  internally serialized music-state collector.
- [x] Replace Block Blast's procedural program with a semantic adapter over
  `AudioRepository`.
- [x] Remove the abandoned game-local platform players and session policy
  controller.
- [x] Wire the adapter through the retained Metro session graph and cover it
  with graph and mapping tests.
- [x] Keep generic procedural audio unchanged for Counter and future MiniApps.

## Required verification

```bash
./gradlew :core:domain:allTests :core:data:allTests
./gradlew :feature:root:allTests
./gradlew :game:blockblast:allTests :game:blockblast:validateMiniAppDependencies
./gradlew :game:blockblast:compileAndroidMain :game:blockblast:compileKotlinIosSimulatorArm64
./gradlew :composeApp:compileAndroidMain :composeApp:linkDebugFrameworkIosSimulatorArm64
```

After automated checks, verify audible playback once on Android and iOS:

1. all three music tracks can start;
2. all five feedback clips play;
3. Music and SFX settings suppress the correct channel;
4. background/foreground transitions stop and resume music correctly;
5. leaving Block Blast stops music.
