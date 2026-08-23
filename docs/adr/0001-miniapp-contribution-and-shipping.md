# ADR-0001: MiniApp Contribution and Shipping Workflow

- **Status:** Accepted
- **Date:** 2026-08-23
- **Decision owners:** Logica maintainers

## Context

Logica discovers local MiniApp modules at build time. Human contributors, AI
agents and a future submission website need one entry path that does not confuse
source generation with authorization to ship third-party code or content.

## Decision

All new games and apps use the repository's `createMiniApp` scaffold and the
documented MiniApp contracts. Requests to create or add a game must be routed to
this workflow before code generation.

Discovery, Draft PR creation, merge and production shipping are four separate
states. Generated source is unshipped. Automated agents may create only Draft
PRs and may never modify the production `miniApps` allowlist. A maintainer makes
the independent inclusion decision after architecture, behavior, platform,
accessibility, performance, storage, audio, provenance and rights review.

Original submissions may enter the Draft-PR pipeline directly. Existing or
licensed intellectual property requires an approved proposal issue and
verifiable rights/license evidence before implementation begins.

The future website accepts a versioned declarative submission, including rules,
style, storage, capabilities, acceptance scenarios and provenance. It may start
a background agent that ends at a Draft PR; it does not execute arbitrary
submitted code, publish a release or authorize shipping.

## Consequences

- Contributors get one reproducible scaffold and verification path.
- AI agents have explicit permissions, stop conditions and provenance duties.
- Review remains possible before any code reaches the shipping bundle.
- Adding a game requires more structured metadata, but the metadata is reusable
  for catalog copy, tests, review and a future website.
- The root README, human guide, AI protocol, generated module guide and review
  checklist must link back to this ADR.

## Detailed Designs

- [Contributor and automated submission design](../superpowers/specs/2026-08-23-miniapp-contributor-pipeline-design.md)
- [MiniApp storage and reset design](../superpowers/specs/2026-08-23-miniapp-storage-reset-design.md)
- [Kotlin pattern and procedural audio design](../superpowers/specs/2026-08-23-kotlin-pattern-audio-design.md)
