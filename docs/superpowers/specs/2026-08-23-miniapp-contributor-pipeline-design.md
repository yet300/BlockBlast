# MiniApp Contributor and Automated Submission Design

**Status:** Approved design

## Goal

Make creation of a reviewable game predictable for humans, local AI agents and
a future website-driven background agent, without turning generation into
production authorization.

The stable architecture decision is
[ADR-0001: MiniApp contribution and shipping workflow](../../adr/0001-miniapp-contribution-and-shipping.md).
The root README must keep a prominent link to that ADR so an agent discovering
the repository can immediately offer the official scaffold workflow when a
user asks to create or add a game.

## Documentation Deliverables

- `CONTRIBUTING_MINIAPP.md`: human workflow and acceptance requirements.
- `docs/miniapp/AI_CONTRIBUTOR_PROTOCOL.md`: deterministic agent protocol,
  permissions and stop conditions.
- `docs/miniapp/submission.schema.json`: machine-readable website/agent input.
- `docs/miniapp/review-checklist.md`: maintainer and PR checklist.
- `docs/adr/0001-miniapp-contribution-and-shipping.md`: stable decision and
  rationale.
- Root `README.md`: visible **Create a game / MiniApp** entry linking the human
  guide, AI protocol and ADR.
- Generated module `AGENTS.md`: links the AI protocol and exact module gates.

Until the detailed guides are implemented, README and `AGENTS.md` point to the
ADR and the existing `createMiniApp` command rather than to nonexistent files.

## Agent Discovery Rule

When a user says they want to create, add, port or contribute a game/MiniApp,
an agent must first offer the repository's official scaffold/submission
workflow. It must not invent an arbitrary Gradle module or start from a copied
game. The agent gathers the submission fields, classifies rights provenance,
and explains that discovery, Draft PR creation, merge and production allowlist
inclusion are separate decisions.

## Rights and Originality Policy

Accepted paths:

1. An original game with original code, visual identity, text and audio.
2. Familiar mechanics with a new name and independently created expression;
   distinctive branding, characters, artwork, sounds and text are excluded.
3. Existing or licensed intellectual property only after a proposal issue is
   approved and the contributor supplies verifiable rights/license evidence.

If the rights holder wants to contribute existing content, the issue and proof
still precede agent implementation. AI-generated content retains model/tool,
prompt and source-reference provenance; calling an asset AI-generated is not
proof that it is original or distributable.

## Submission Schema

The versioned schema contains at least:

- MiniApp ID, display name, category and authors;
- summary, long description, rules, controls and session flow;
- supported orientation/device classes and accessibility behavior;
- visual style, audio style and design references;
- storage schema and data categories;
- requested host capabilities;
- provenance for code, art, audio, fonts, references, licenses, AI tools and a
  prompt archive;
- acceptance scenarios and known limitations;
- optional approved proposal-issue URL for existing/licensed IP.

Schema validation happens before repository mutation.

## Automated Agent Pipeline

1. Validate the submission and rights classification.
2. Verify ID, module path and that the ID is not already shipped/discovered.
3. Require an approved issue for existing/licensed IP.
4. Create a branch from the requested target.
5. Run `createMiniApp`; never hand-roll the framework shell.
6. Implement the component/session/UI, namespaced storage and optional audio.
7. Add localization and accessibility semantics.
8. Run dependency, contract, platform, resource and lifecycle tests.
9. Run license/provenance checks and attach their reports.
10. Create a **Draft PR** containing behavior, screenshots, test evidence,
    provenance, AI disclosure, limitations and an explicit **NOT ALLOWLISTED**
    status.

The agent cannot edit the production `miniApps` allowlist, enable a release,
touch credentials/signing, push directly to protected branches or conceal
failed checks and provenance warnings.

## Review Gates

- Architecture and dependency boundaries.
- Gameplay rules and deterministic acceptance scenarios.
- Adaptive UI, accessibility, localization and lifecycle correctness.
- Performance, memory and background-work limits.
- Storage namespace/reset compliance.
- Procedural/sample audio CPU, clipping and provenance compliance.
- Copyright, trademark and dependency-license evidence.
- Android and iOS verification.
- Maintainer product decision.
- A separate, explicit production allowlist change after merge readiness.

Implementation merge does not imply release inclusion. Only the maintainer can
approve the independent allowlist change.

## Future Website Boundary

The website collects and validates `submission.schema.json`, stores the prompt
and provenance archive, and starts a background agent job. It does not execute
submitted Kotlin/JavaScript and cannot alter the production allowlist. The job
streams status, produces artifacts and terminates at a Draft PR for maintainer
review.

## Verification

Test schema valid/invalid fixtures, rights-classification stop conditions,
duplicate IDs, scaffold invocation, forbidden dependencies, missing
provenance, failed platform gates, Draft-only PR output and explicit rejection
of any allowlist mutation. A dry-run fixture must prove that an original game
can reach Draft PR without an issue, while a licensed-IP submission cannot
start without an approved issue.
