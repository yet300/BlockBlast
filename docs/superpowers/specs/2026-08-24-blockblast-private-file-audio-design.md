# Block Blast Private File Audio Design

**Date:** 2026-08-24

**Status:** Approved design, pending implementation plan

## Context

Block Blast originally used bundled MP3 music and voice feedback. It was later
migrated to the generic procedural `MiniAppAudio` engine. That migration made
the game's existing soundtrack harder to preserve and exposed an unnecessary
asset-audio design problem to the whole MiniApp framework.

Bundled file playback is currently implemented as shared application
infrastructure through `AudioRepository`, `AudioFileProvider`,
`DefaultAudioRepository`, and platform `PlatformSoundPlayer` implementations.
Those abstractions are only backed by Block Blast resources. Their placement in
`core` and lifecycle ownership in `feature:root` falsely imply that bundled
audio is a supported capability for every MiniApp.

## Decision

Bundled audio is a private Block Blast implementation detail. The public
MiniApp framework continues to support procedural audio only. Block Blast stops
declaring or playing procedural music and SFX and restores its existing MP3
music and voice feedback through session-scoped, internal implementations in
`:game:blockblast`.

This decision deliberately does not create a reusable asset-audio API. A common
API may be designed later if a second independently authored MiniApp presents a
concrete bundled-audio requirement.

## Goals

- Restore Block Blast's three existing music tracks and five existing voice
  feedback effects.
- Keep every bundled-audio contract and platform implementation internal to
  `:game:blockblast`.
- Respect the app's existing music and SFX preferences without exposing raw
  Settings storage to the game.
- Silence audio whenever the session is obscured, backgrounded, or destroyed.
- Remove the obsolete shared file-audio API and Root lifecycle bridge.
- Keep audio failures non-fatal to game launch and gameplay.

## Non-goals

- Adding bundled file playback to `MiniAppAudio`.
- Adding audio assets to the MiniApp contributor convention or scaffolder.
- Replacing the procedural engine used by Counter or future MiniApps.
- Introducing Korlibs, Media3, another audio engine, remote streaming, or
  generated Kotlin `ByteArray` assets.
- Reworking the shared procedural renderer or its Android/iOS sinks.
- Changing the existing music and voice recordings.

## Architecture

### Shared framework

`MiniAppAudio`, `MiniAppAudioEngine`, procedural programs, presets, session
opening, visibility suppression, and teardown remain unchanged for MiniApps
that use procedural audio. Block Blast continues to receive a session context
that contains `MiniAppAudio` because it implements the same generic plugin
contract, but it does not call that facade.

No `usesProceduralAudio` flag is added to the manifest. An unused host facade is
less costly than expanding the stable plugin API for one implementation detail.

### Block Blast common code

`:game:blockblast` owns these internal concepts:

- `BlockBlastAudioPlayer`: the existing game-semantic interface used by game
  components and stores.
- `BlockBlastAudioAssets`: typed constants mapping the three music tracks and
  every `FeedbackType` to an existing MP3 filename.
- `DefaultBlockBlastAudioPlayer`: the session policy controller. It combines
  requested playback, preferences, visibility, and destruction, then delegates
  actual playback to the platform player.
- `BlockBlastPlatformAudioPlayer`: an internal platform boundary with only
  `playVoice`, `startMusic`, `stopMusic`, and `release` operations.
- `BlockBlastAudioFileProvider`: an internal adapter over the Block Blast
  Compose resource namespace. It is not exported from the game module.

`BlockBlastAudioPlayer` remains semantic: callers request a feedback type or
music state and never pass filenames or DSP commands.

### Android implementation

The current proven Android strategy moves from `:core:data` into
`:game:blockblast`:

- `SoundPool` loads and plays short voice effects.
- `MediaPlayer` streams music using an `AssetFileDescriptor`.
- Compose Resources remains only an internal packaging mechanism; the player
  resolves paths through the Block Blast resource provider.
- Missing assets, decoder failures, and invalid player state are caught and
  treated as silence.

### iOS implementation

The current proven iOS strategy also moves into `:game:blockblast`:

- the Block Blast resource provider reads the bundled resource bytes;
- bytes are materialized as temporary files;
- `AVAudioPlayer` plays music and voice effects;
- successfully prepared voice players are cached for the session;
- cancellation and generation checks prevent a stopped or destroyed session
  from publishing a late player.

This retains Compose Resources only inside Block Blast and avoids building a
cross-MiniApp native resource packaging system. The implementation must keep
file loading and temporary-file writes off the realtime audio callback and
must release all players when the session is destroyed.

## Settings and lifecycle policy

`FeedbackPreferences` gains `musicEnabled: StateFlow<Boolean>`. Its existing
Settings-backed implementation remains app-owned, so Block Blast neither
imports Multiplatform Settings nor knows physical keys.

The session-scoped Block Blast audio controller combines:

1. whether Block Blast has requested music;
2. `FeedbackPreferences.musicEnabled`;
3. whether `MiniAppVisibilitySource` is `ACTIVE`;
4. whether the session lifecycle is alive.

Music plays only while all four conditions permit it. Opening Settings,
backgrounding the app, leaving Block Blast, disabling music, or destroying the
session stops playback. Returning to the active game or re-enabling music
resumes the requested playlist without requiring a new game event.

Voice feedback plays only when SFX is enabled, visibility is `ACTIVE`, and the
session is alive. Suppressed feedback is dropped rather than queued.

Session destruction is idempotent: it stops music, releases platform players,
cancels controller collection, and ignores subsequent calls.

## Playback behavior

The restored playlist contains the existing `block.mp3`, `feltwood.mp3`, and
`mossy.mp3` assets. Track selection preserves the existing non-repeating
playlist behavior.

Feedback maps exactly as follows:

| Game feedback | Bundled asset |
|---|---|
| `GOOD` | `voice_good.mp3` |
| `GREAT` | `voice_great.mp3` |
| `AMAZING` | `voice_amazing.mp3` |
| `EXCELLENT` | `voice_excellent.mp3` |
| `UNBELIEVABLE` | `voice_unbelievable.mp3` |

Starting music is idempotent. Stopping music clears the current request so a
later visibility change cannot restart a completed session's soundtrack.
Starting a new playing round requests the playlist again through the existing
game component behavior.

## Dependency and ownership changes

The following obsolete shared APIs are removed:

- `core/domain/.../AudioRepository.kt`
- `core/domain/.../AudioFileProvider.kt`
- `core/data/.../DefaultAudioRepository.kt`
- `core/data/.../PlatformSoundPlayer.kt`
- Android and native shared platform sound-player implementations and their DI
  bindings

`feature:root` no longer injects `AudioRepository` or forwards application
foreground/background events to a file-audio service. Generic procedural audio
lifecycle remains owned by `MiniAppRuntimeCoordinator`; Block Blast file audio
uses its own session visibility and lifecycle.

The Block Blast module must not expose its player, assets, filenames, or
resource provider across the module boundary.

## Failure handling

Audio is best-effort. Missing files, unreadable resources, temporary-file
failures, decoder failures, unavailable audio sessions, and invalid native
player states produce silence rather than exceptions escaping into game or
Root code. A failure does not prevent plugin discovery, session creation, game
input, persistence, result navigation, or session destruction.

Repeatedly missing SFX are remembered for the session to avoid repeated I/O.
Asynchronous loads check cancellation and session generation before starting
playback.

## Testing and verification

Common tests cover:

- every `FeedbackType` maps to the expected MP3;
- the playlist contains exactly the three existing music assets;
- SFX preference and visibility suppress voice playback;
- music preference and visibility stop and resume requested music;
- explicit `stopMusic` prevents later automatic restart;
- destruction releases once and rejects late playback;
- game-semantic callers never pass raw filenames.

Existing Root tests are updated to prove Root no longer depends on or forwards
lifecycle to legacy file audio. Existing Block Blast graph tests prove the
private player is session-scoped and destroyed with its retained child graph.

Verification includes:

```bash
./gradlew :core:domain:allTests
./gradlew :core:data:allTests
./gradlew :feature:root:allTests
./gradlew :game:blockblast:allTests
./gradlew :game:blockblast:compileAndroidMain
./gradlew :game:blockblast:compileKotlinIosSimulatorArm64
./gradlew :composeApp:compileAndroidMain
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

Because compilation cannot prove native playback, the restored soundtrack and
all five feedback clips must also be checked once on Android and once on an iOS
simulator or device, including Settings open/close and app background/foreground
transitions.

## Documentation

`AGENTS.md` and contributor audio documentation are updated to state:

- the public MiniApp audio capability remains procedural;
- Block Blast is a deliberate private bundled-audio exception;
- contributors must not copy its resource/player implementation as a public
  framework pattern;
- a second bundled-audio use case requires a new architecture decision rather
  than importing Block Blast internals.
