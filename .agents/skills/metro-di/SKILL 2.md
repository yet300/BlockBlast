---
name: metro-di
description: Help another Codex agent set up and modify Metro DI in Kotlin projects. Use when the task mentions Metro, `dev.zacsweers.metro`, `@DependencyGraph`, `@ContributesBinding`, `@ContributesTo`, graph creation, multi-module DI wiring, Kotlin Multiplatform integration, or `expect`/`actual` patterns with Metro.
---

# Metro DI

## Overview

Use this skill to make correct Metro architecture decisions before editing Kotlin or Gradle files. Favor the smallest Metro change that matches the project's shape: single-module, multi-module, or Kotlin Multiplatform.

## Workflow

1. Inspect the project shape before proposing Metro code.
2. Identify whether the target is:
   - a single-module Kotlin/JVM or Android project,
   - a multi-module project that needs aggregation,
   - a Kotlin Multiplatform project with `commonMain` plus platform source sets.
3. Identify whether the task is about:
   - initial Metro setup,
   - adding or changing bindings,
   - creating or extending a graph,
   - fixing platform-specific graph placement in KMP.
4. Read [references/metro-docs.md](references/metro-docs.md) before making non-trivial Metro changes.

## Decision Rules

### Initial setup

- Prefer applying the Gradle plugin:

```kotlin
plugins {
  kotlin("multiplatform") // or jvm, android, etc
  id("dev.zacsweers.metro")
}
```

- Assume the Gradle plugin wires the compiler plugin and runtime automatically unless the project already manages Metro artifacts manually.
- Prefer `createGraph<GraphType>()` for simple graph creation.
- Prefer `@DependencyGraph.Factory` only when runtime inputs or included bindings are required.

### Single-module projects

- Prefer explicit `@Binds` or `@Provides` when the graph and implementations live together and aggregation is unnecessary.
- Use `@Inject` for constructor-injected classes.
- Keep the graph simple and local when there is no module boundary forcing aggregation.

### Multi-module projects

- Prefer scope-based aggregation with `@ContributesBinding` and `@ContributesTo`.
- Treat `@DependencyGraph(scope = ...)` as the merge point for contributions from other modules.
- Prefer `@ContributesBinding` for api/impl style bindings that should be discovered automatically across modules.
- Prefer `@ContributesTo` for binding containers or provider interfaces that contribute to the graph scope.
- Do not reintroduce explicit local `@Binds` for every contributed implementation unless the module design specifically requires non-aggregated bindings.

### Kotlin Multiplatform projects

- Distinguish between common contracts and final platform graphs.
- When platform-specific contributions exist, do not place the final `@DependencyGraph` in `commonMain`.
- Prefer this pattern:
  - define the canonical graph contract in `commonMain` without `@DependencyGraph`,
  - define `AndroidAppGraph`, `JvmAppGraph`, `IosAppGraph`, etc. in platform source sets,
  - annotate the platform graph with `@DependencyGraph`.
- Expect Metro to aggregate only what the source set can actually see.

### `expect` / `actual`

- Do not invent `expect fun createGraph()` patterns for Metro unless the existing codebase already uses that abstraction intentionally.
- Prefer `expect` / `actual` for platform implementations and APIs, not for bypassing Metro graph placement rules.
- If a dependency differs by platform, keep the interface or usage contract in `commonMain` and bind or provide the platform implementation in the platform source set where the final graph lives.
- If annotations are used on `expect` / `actual` declarations, verify whether the annotation must also be present on the `actual` declaration before editing adjacent code.

## Guardrails

- Do not put a common `@DependencyGraph` in `commonMain` if the graph must consume platform-only contributions.
- Do not suggest KAPT or KSP as required Metro setup. Metro is a compiler-plugin-based DI framework.
- Do not assume `@ContributesBinding` is always better than `@Binds`; choose based on module boundaries.
- Do not create extra abstractions when `createGraph()` or an existing graph factory already solves the problem.
- Do not move Metro code into unrelated modules unless the task explicitly requires architectural restructuring.

## Practical Checks

- Verify where the target graph is declared.
- Verify which source set owns each implementation.
- Verify whether the bound type is visible from the graph's source set.
- Verify whether the project already uses aggregation scopes.
- Verify whether a binding is better expressed as:
  - constructor injection,
  - `@Provides`,
  - `@Binds`,
  - `@ContributesBinding`,
  - `@ContributesTo`.

## Reference Usage

- Read [references/metro-docs.md](references/metro-docs.md) for:
  - plugin setup and graph creation,
  - single-module vs multi-module binding strategy,
  - KMP graph placement rules,
  - practical notes for `expect` / `actual`.

## Output Style

- Explain Metro changes in terms of project shape first, then specific annotations or Gradle edits.
- When proposing KMP changes, state clearly which source set should own the final graph and why.
- When proposing multi-module changes, name the aggregation scope and the contributing module boundaries explicitly.
