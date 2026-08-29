# AI Contributor Protocol

This protocol applies when an agent is asked to create, add, port or implement
a MiniApp in this repository.

## Required sequence

1. Collect and validate the submission fields in
   [`submission.schema.json`](submission.schema.json).
2. Classify the rights situation before writing source.
3. Stop for existing or licensed IP unless an approved proposal issue and
   verifiable rights evidence are present.
4. Verify that the ID, project path and package names are valid and unused.
5. Use `createMiniApp`; for a game, prefer `-PminiAppProfile=game`. Never
   hand-roll a new MiniApp module or copy a complete existing game.
6. Implement rules and UI inside the generated module boundaries.
7. Add deterministic engine, lifecycle, persistence, accessibility and
   acceptance tests appropriate to the game.
8. Run `:game:<name>:verifyMiniApp`, `git diff --check` and the relevant
   resource/provenance checks.
9. Report the exact commands and results, including any unavailable platform
   gates.
10. End with a reviewable change marked **NOT ALLOWLISTED**.

## Permission boundaries

The agent may create or modify source, tests, resources and documentation in
the requested MiniApp module. It may update that module's build file only with
approved inward dependencies.

The agent must not:

- modify `settings.gradle.kts`'s `miniApps` block;
- add a dependency to the production bundle;
- change release, signing, credentials or store metadata;
- push directly to protected branches or create a non-draft release PR;
- conceal failed tests, missing provenance or unavailable platform checks;
- import raw Settings, platform audio, native ad SDKs, feature modules,
  application modules or another game/sample;
- copy code, art, audio, fonts, branding or distinctive expression from a
  reference game.

## Stop conditions

Stop and report the blocker when:

- the submission is missing required fields;
- rights or license provenance is missing or ambiguous;
- the requested ID or project path already exists;
- the user asks to ship or allowlist the game without maintainer review;
- a required platform/toolchain is unavailable;
- a verification command fails and the root cause cannot be resolved safely;
- implementation would require a new cross-feature dependency or host API.

Do not silently downgrade a failed gate to a warning. Distinguish `passed`,
`failed`, `blocked-by-environment` and `not-run` in the final report.

## Verification report

The agent's final report must include:

- MiniApp ID and project path;
- files and behavior changed;
- exact verification commands and outcomes;
- known limitations and unverified platform work;
- provenance/license evidence location;
- explicit `NOT ALLOWLISTED` status.

## Architecture invariants

The plugin receives one `MiniAppSessionContext`. Persistent values use the
session storage facade and local snake-case names. New procedural audio uses
the public audio API or reusable presets. Root owns navigation, visibility,
Back, Settings, toolbar and ad containers. The game owns its rules and game
state. A session's Metro child graph is retained by the session and destroyed
with it.

The repository's [ADR-0001](../adr/0001-miniapp-contribution-and-shipping.md)
and [human contributor guide](../../CONTRIBUTING_MINIAPP.md) are normative
references when this protocol is incomplete.
