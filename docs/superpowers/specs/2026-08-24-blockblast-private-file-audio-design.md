# Block Blast Bundled File Audio Design

**Date:** 2026-08-24

**Status:** Approved and implemented

## Context

Block Blast originally used three bundled MP3 music tracks and five bundled
voice effects. A later migration routed the game through the generic
procedural `MiniAppAudio` engine, even though preserving the existing
soundtrack is a Block Blast-specific requirement.

The application already has a proven file-audio pipeline: `AudioRepository`
and `AudioFileProvider` contracts in `:core:domain`, their settings-aware
implementation and platform players in `:core:data`, and foreground/background
forwarding in `:feature:root`.

## Decision

Keep that existing application-owned file-audio pipeline and use it only for
Block Blast. Do not duplicate Android or iOS players in the game module and do
not add bundled-file playback to the public MiniApp framework.

Block Blast owns only:

- `BlockBlastAudioPlayer`, the semantic interface used by its game logic;
- `BlockBlastAudioAssets`, which maps game events to bundled filenames;
- `DefaultBlockBlastAudioPlayer`, a thin adapter from those semantic commands
  to `AudioRepository`.

The common platform pipeline continues to own `SoundPool`/`MediaPlayer` on
Android, `AVAudioPlayer` on iOS, settings gates, playlist selection, and app
lifecycle handling. `AudioRepository` commands are synchronous because every
operation only updates in-memory state or delegates immediately; serialized
music transitions remain inside `DefaultAudioRepository`'s owned coroutine.

## MiniApp boundary

Generic MiniApps continue to receive `MiniAppSessionContext.audio` and author
procedural programs. Block Blast receives that generic facade as part of the
session context but deliberately does not call it. No manifest flag and no
asset-audio contributor API are added.

`AudioRepository` is legacy application infrastructure, not a supported
MiniApp authoring contract. A second game needing file assets requires a new
architecture decision; contributors must not copy Block Blast's resource
provider or depend directly on native audio APIs.

## Playback mapping

Music playlist:

- `block.mp3`
- `feltwood.mp3`
- `mossy.mp3`

Feedback mapping:

| Game feedback | Bundled asset |
|---|---|
| `GOOD` | `voice_good.mp3` |
| `GREAT` | `voice_great.mp3` |
| `AMAZING` | `voice_amazing.mp3` |
| `EXCELLENT` | `voice_excellent.mp3` |
| `UNBELIEVABLE` | `voice_unbelievable.mp3` |

## Lifecycle and settings

`DefaultAudioRepository` gates voice playback with `sfxEnabled`, and music
with `musicEnabled`, requested playlist state, and application foreground
state. Block Blast requests music while its Playing component is active and
stops it on game over or component destruction. Root continues to forward
application lifecycle events to the repository.

This preserves the previous behavior without a second session-owned player.
Opening an in-app sheet does not destroy the game or clear its music request;
global settings changes still take effect reactively.

## Failure handling

Bundled playback remains best-effort. Missing resources, decoder failures,
invalid native player state, or temporary-file failures result in silence and
must not prevent game launch, input, persistence, navigation, or teardown.

## Verification

Automated checks cover exact mappings, semantic routing, repository settings
and lifecycle behavior, Metro graph wiring, Android/iOS compilation, and the
MiniApp dependency boundary. Native compilation cannot prove audible output,
so music, every voice clip, settings toggles, and background/foreground
transitions still require one Android and one iOS runtime check.
