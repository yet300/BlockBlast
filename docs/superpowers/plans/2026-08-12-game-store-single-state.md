# Game Store Single-State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the stateful singleton `GameEngine` with pure domain transitions and make `GameStore` the sole gameplay-state owner.

**Architecture:** `GameSessionReducer` maps explicit `GameState` inputs to immutable transitions. `GameStore` stores `GameState` directly and owns persistence, audio, analytics and navigation side effects within its executor lifecycle.

**Tech Stack:** Kotlin Multiplatform, Kotlin coroutines, kotlinx.serialization, MVIKotlin, Decompose, Metro DI, kotlin.test.

---

### Task 1: Pure game-session reducer

**Files:**
- Create: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/GameSessionReducer.kt`
- Create: `core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/GameSessionReducerTest.kt`
- Modify: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/model/GameState.kt`

- [ ] Write tests that construct a complete `GameState`, invoke `place`, `startNewGame`, `restore`, and `revive`, and assert returned state and facts without collecting a Flow.
- [ ] Run `./gradlew :core:domain:allTests --tests '*GameSessionReducerTest*'` and confirm compilation or assertions fail because the reducer API is absent.
- [ ] Implement `GameTransition`, rejection reasons, round-start transition and stateless `GameSessionReducer` with explicit state parameters.
- [ ] Run the focused reducer tests and the full `:core:domain:allTests` suite.

### Task 2: Store-owned persistence and initialization

**Files:**
- Create: `feature/game/src/commonMain/kotlin/ge/yet/blockblast/feature/game/store/GameSaveCoordinator.kt`
- Create: `feature/game/src/commonTest/kotlin/ge/yet/blockblast/feature/game/store/GameSaveCoordinatorTest.kt`
- Modify: `feature/game/src/commonMain/kotlin/ge/yet/blockblast/feature/game/store/GameInitializer.kt`

- [ ] Write coroutine tests proving that scheduled saves debounce and `flush` prevents an older snapshot from overwriting the explicit one.
- [ ] Run the focused tests and confirm they fail because `GameSaveCoordinator` is absent.
- [ ] Implement the per-Store coordinator with caller-supplied scope, generation invalidation, `cancelAndJoin`, mutex serialization and cancellation propagation.
- [ ] Refactor `GameInitializer` to return `Result(state, source, roundStart)` using `GameSessionReducer`, persisted save and settings values.
- [ ] Run focused feature tests.

### Task 3: Make GameStore the sole state owner

**Files:**
- Modify: `feature/game/src/commonMain/kotlin/ge/yet/blockblast/feature/game/store/GameStore.kt`
- Modify: `feature/game/src/commonMain/kotlin/ge/yet/blockblast/feature/game/store/GameStoreFactory.kt`
- Modify: `feature/game/src/commonMain/kotlin/ge/yet/blockblast/feature/game/integration/Mappers.kt`
- Delete: `feature/game/src/commonMain/kotlin/ge/yet/blockblast/feature/game/store/GameStoreState.kt`
- Modify: `feature/game/src/commonTest/kotlin/ge/yet/blockblast/feature/game/store/GameStoreFactoryTest.kt`

- [ ] Add a test creating two stores from one factory and prove that a move in one Store cannot mutate the other Store's state.
- [ ] Run the test and confirm it fails against the shared singleton engine architecture.
- [ ] Change `GameStore` to `Store<Intent, GameState, Label>` and reduce snapshots directly to `GameState`.
- [ ] Replace all engine collectors with synchronous reducer transitions and explicit executor-side effects.
- [ ] Preserve terminal-save, revive rollback, analytics, review and music behavior in Store tests.
- [ ] Run `./gradlew :feature:game:allTests`.

### Task 4: Remove the legacy engine and update composition tests

**Files:**
- Delete: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/GameEngine.kt`
- Delete: `core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/GameEngineTest.kt`
- Modify: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/di/DomainBindings.kt`
- Modify: `composeApp/src/commonMain/kotlin/ge/yet3/blokblast/screen/game/DragDropState.kt`
- Modify: `feature/game/src/commonTest/kotlin/ge/yet/blockblast/feature/game/DefaultGameComponentTest.kt`
- Modify: `feature/root/src/commonTest/kotlin/ge/yet/blockblast/feature/root/DefaultRootComponentTest.kt`

- [ ] Move retained engine behavior coverage to `GameSessionReducerTest` and delete the obsolete state-holder tests.
- [ ] Replace test fixtures and component factories with `GameSessionReducer` dependencies.
- [ ] Update stale documentation references to the domain placement rule.
- [ ] Run domain, game and root tests.
- [ ] Compile shared Android code with `./gradlew :composeApp:compileAndroidMain`.

### Task 5: Structural verification

**Files:**
- Modify if needed: `docs/superpowers/specs/2026-08-12-game-store-single-state-design.md`

- [ ] Search production Kotlin for `GameEngine`, `engine.state`, `engine.events`, `GameStoreState`, `MutableStateFlow<GameState>` and `MutableSharedFlow<GameEvent>`; expect no legacy ownership references.
- [ ] Inspect the final diff for unrelated changes and accidental generated files.
- [ ] Run all four verification commands from the design document and record exact results.
