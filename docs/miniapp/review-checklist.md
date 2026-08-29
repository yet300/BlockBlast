# MiniApp Review Checklist

Use this checklist before changing the production allowlist. A checked box
means that evidence is attached to the change or can be reproduced locally.

## Submission and rights

- [ ] Submission matches [`submission.schema.json`](submission.schema.json).
- [ ] MiniApp ID, project path and package names are unique and valid.
- [ ] Code provenance is recorded.
- [ ] Art, audio, fonts, references and licenses are recorded.
- [ ] Existing or licensed IP has an approved proposal issue and rights evidence.
- [ ] AI tools, prompts and generated-source disclosure are included where applicable.

## Architecture

- [ ] Module applies `logica.miniapp`.
- [ ] Dependencies flow inward and pass `validateMiniAppDependencies`.
- [ ] No feature, application, native-ad, platform-audio, raw Settings or
      sibling-game dependency was introduced.
- [ ] Host-owned navigation, Back, Settings, toolbar, visibility and ads remain
      outside the plugin.
- [ ] Session-owned state and child graph are destroyed with the session.
- [ ] No production allowlist change is mixed into contributor implementation.

## Gameplay and state

- [ ] Rules are deterministic where deterministic behavior is expected.
- [ ] Core rules are tested independently of Compose and platform APIs.
- [ ] Invalid actions and terminal states are covered.
- [ ] Persistence uses `MiniAppSessionContext.storage` and local snake-case names.
- [ ] Snapshot validation, reset behavior and schema/version changes are covered.
- [ ] Backgrounding, recreation, relaunch and session destruction are covered.

## UI and accessibility

- [ ] Compact and wide layouts are verified.
- [ ] Touch, swipe, drag or keyboard controls have clear semantics.
- [ ] Important state changes have announcements where needed.
- [ ] Focus behavior is defined for overlays and result states.
- [ ] Reduced-motion behavior is supported for meaningful animations.
- [ ] User-visible strings are Compose resources and have a localization path.
- [ ] The plugin does not create inert catalog actions or duplicate host chrome.

## Audio and performance

- [ ] New audio uses the public procedural API or approved reusable presets.
- [ ] Audio commands are session-bound and visibility/lifecycle safe.
- [ ] Audio has deterministic tests or render assertions where applicable.
- [ ] No unbounded work, allocations or blocking I/O occurs in frame-sensitive code.
- [ ] Android and iOS memory/CPU behavior has been checked.

## Verification

- [ ] `./gradlew :game:<name>:verifyMiniApp` passes.
- [ ] Relevant Android and iOS Simulator compilation passes.
- [ ] Contract, lifecycle, persistence, resource and acceptance tests pass.
- [ ] `git diff --check` passes.
- [ ] Any unavailable platform gate is explicitly reported.
- [ ] Change is marked **NOT ALLOWLISTED** until maintainer approval.

## Maintainer decision

- [ ] Product fit and catalog metadata reviewed.
- [ ] Store/release implications reviewed separately.
- [ ] Explicit allowlist change approved in a separate maintainer change.
