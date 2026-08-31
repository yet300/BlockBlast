# Fruit Merge Next, Fruit and Danger Polish Design

## Goal

Refine the Fruit Merge play surface so the queued fruit, score, drop guide and
danger state communicate the game without extra explanatory copy, while keeping
the single-canvas renderer inexpensive on low-end devices.

## Approved visual direction

The reference screenshot is used for hierarchy and interaction cues, not copied
artwork. The result remains inside the existing Logica Material 3 design system.
The first four merge levels become blueberry, raspberry, strawberry and lime.
They use deliberately flat vector illustration: strong silhouettes, a small
controlled palette, sparse highlights and compact graphic faces. Soft 3D gloss,
generic kawaii rendering and decorative texture are avoided.

## Header hierarchy

The score becomes a reusable UIKit compact two-cell capsule at the top of the game. Each cell
has a quiet label and a bold compact value. Display values use K, M and B suffixes,
keep at most one decimal digit and retain the exact number in semantics.

The queued-fruit card is a light outlined pill inspired by the reference: the
fruit occupies the leading circular area, a small directional chevron separates
it from the trailing NEXT label, and the pill remains readable at compact and
wide breakpoints. Bomb and shake controls keep their existing behavior and stay
in the action row.

## Queued-fruit transfer

An accepted drop creates one transient UI-only transfer event containing the
previous queued level. The event is only created while the engine cooldown is
ready, so rejected spam taps cannot start false animations. The queued card is
measured in root coordinates; the board target is calculated from its measured
bounds and current preview position.

A single full-viewport Canvas draws the transferred fruit along a short quadratic
arc from the card to the board preview rail. Radius interpolates from card size to
the level's board size. The board preview is hidden for the flight and revealed
at completion, preventing duplicate fruit. Reduced-motion mode skips the flight
and reveals the promoted preview immediately.

## Fruit language

- Blueberry: one indigo circular berry, powder ring and unmistakable five-point
  calyx.
- Raspberry: a clustered silhouette built from a small fixed set of drupelets,
  with a restrained leaf cap.
- Strawberry: a tapered heart-like berry drawn as one path, a three-leaf crown
  and sparse ordered seeds.
- Lime: a green citrus circle with dark rind, restrained wedge arcs and a small
  leaf accent.

Faces are positioned per silhouette. They remain small enough that the fruit
identity dominates. The internal enum ordering and persisted names remain
unchanged to preserve existing saves; user-visible names and drawing semantics
change to raspberry and lime.

## Drop guide

The generic dashed line is replaced by a coral horizontal rail with clear arrow
heads on both ends and a descending column of round dots. Dot size and opacity
fall toward the bottom. The current fruit sits above the rail, matching the
reference's visual grammar while retaining the game's current drag behavior.

## Danger feedback

Danger intensity is a pure value derived from the distance between a body's top
edge and the danger line. Within the warning band the fruit receives a warm glow;
close to or across the line it also gets an alarmed mouth and two animated tear
drops. The existing face clock drives glow and tears, so no per-fruit coroutine or
Composable is introduced. Reduced-motion mode naturally produces a static glow
and static tears.

## Performance and accessibility

Board fruits, guide, danger feedback and the transfer overlay remain Canvas
drawing operations with fixed-size geometry. No bitmap allocation, particle
system or per-body Compose node is added. Layout coordinates update only when
layout changes. Exact score values, queued-fruit names and danger descriptions
remain available to accessibility services even when visible copy is compact.

## Verification

Pure tests cover score compaction, warning-band thresholds and the four distinct
visual identities. Compose UI tests retain compact/wide reachability and verify
that cooldown disables drop dispatch. The complete MiniApp, Android app and iOS
framework compilation remain the final acceptance gate.
