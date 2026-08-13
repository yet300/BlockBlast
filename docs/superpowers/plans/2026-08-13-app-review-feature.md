# App Review Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish extracting the reusable app-review flow from Block Blast into `:feature:review`, with `Root` owning the modal slot.

**Architecture:** Block Blast emits only a game-specific `reviewOpportunity` after a qualifying game over. `AppReviewPolicy` owns the app-wide prompt limit, `AppReviewComponent` owns suppression, analytics, and the store-review request, and `RootComponent` coordinates the serialized `ChildSlot`. Compose renders only the component state and callbacks.

**Tech Stack:** Kotlin Multiplatform, Decompose `ChildSlot`, Metro DI, Compose Multiplatform, kotlinx.coroutines, kotlin.test.

---

### Task 1: Complete the review feature contract and behavior

**Files:**
- Modify: `feature/review/src/commonMain/kotlin/ge/yet/game/feature/review/AppReviewComponent.kt`
- Modify: `feature/review/src/commonMain/kotlin/ge/yet/game/feature/review/DefaultAppReviewComponent.kt`
- Create: `feature/review/src/commonMain/kotlin/ge/yet/game/feature/review/AppReviewPolicy.kt`
- Create: `feature/review/src/commonMain/kotlin/ge/yet/game/feature/review/DefaultAppReviewPolicy.kt`
- Create: `feature/review/src/commonMain/kotlin/ge/yet/game/feature/review/di/ReviewBindings.kt`
- Create tests under `feature/review/src/commonTest/kotlin/ge/yet/game/feature/review/`

- [ ] Write failing tests proving that policy atomically consumes an opportunity below the lifetime limit and rejects one at the limit.
- [ ] Write failing component tests proving shown/closed/requested/suppressed analytics, store request, suppression, dismissal, and duplicate-click protection.
- [ ] Run `rtk ./gradlew :feature:review:allTests` and confirm compilation/test failure is caused by missing contracts.
- [ ] Implement `AppReviewPolicy.tryAcquirePrompt()`, `AppReviewComponent.Factory`, side effects, error analytics, and Metro bindings.
- [ ] Run `rtk ./gradlew :feature:review:allTests` and confirm success.

### Task 2: Remove app-review ownership from Block Blast result

**Files:**
- Modify: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/component/game/store/GameStore.kt`
- Modify: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/component/game/store/GameStoreFactory.kt`
- Modify: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/component/game/GameComponent.kt`
- Modify: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/component/result/GameResultComponent.kt`
- Modify: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/component/result/DefaultGameResultComponent.kt`
- Delete: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/component/review/AppReviewComponent.kt`
- Update matching Block Blast tests.

- [ ] Change tests so a qualifying round emits `reviewOpportunity` and marks the round, without reading or incrementing the global prompt count.
- [ ] Remove review component/state/store request/analytics from `DefaultGameResultComponent` and its factory contract.
- [ ] Keep only Block Blast score, best-score delta, and per-round duplicate qualification in the game store.
- [ ] Run `rtk ./gradlew :game:blockblast:allTests` and confirm success.

### Task 3: Let Root own the review slot

**Files:**
- Modify: `feature/root/src/commonMain/kotlin/ge/yet/game/feature/root/RootComponent.kt`
- Modify: `feature/root/src/commonMain/kotlin/ge/yet/game/feature/root/DefaultRootComponent.kt`
- Modify: `feature/root/src/commonTest/kotlin/ge/yet/game/feature/root/DefaultRootComponentTest.kt`

- [ ] Add failing tests proving eligible opportunities open `SheetChild.AppReview`, ineligible opportunities do not, dismiss/back closes review before leaving Result, and actions dismiss once.
- [ ] Inject `AppReviewPolicy` and `AppReviewComponent.Factory` into Root.
- [ ] Add serializable `SheetConfig.AppReview` with primitive analytics values and create the review child through its factory.
- [ ] Route outside-tap/back dismissal through the review component before dismissing the slot.
- [ ] Run `rtk ./gradlew :feature:root:allTests` and confirm success.

### Task 4: Complete resources, Compose UI, and Metro graph wiring

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/ge/yet3/blokblast/screen/review/AppReviewContent.kt`
- Modify: `composeApp/src/commonMain/kotlin/ge/yet3/blokblast/screen/root/RootSheet.kt`
- Move four `review_prompt_*` strings from every `game/blockblast/.../values*` file to matching `composeApp/.../values*` file.
- Modify: `composeApp/src/androidMain/kotlin/ge/yet3/blokblast/di/AndroidAppGraph.kt`
- Modify: `composeApp/src/iosMain/kotlin/ge/yet3/blokblast/di/NativeAppGraph.kt`
- Modify: `AGENTS.md`

- [ ] Render app-review using app resources and callbacks only; no repository or routing logic in Compose.
- [ ] Register `ReviewBindings` in both platform graphs.
- [ ] Update module documentation and dependency table.
- [ ] Run `rtk ./gradlew :composeApp:compileAndroidMain :composeApp:linkDebugFrameworkIosSimulatorArm64` and confirm success.

### Task 5: Full regression verification

- [ ] Scan production code to confirm Block Blast no longer references `AppReviewComponent`, `StoreReviewRepository`, `reviewPromptCount`, `incrementReviewPromptCount`, or `REVIEW_MAX_PROMPTS`.
- [ ] Run `rtk git diff --check`.
- [ ] Run `rtk ./gradlew :feature:review:allTests :game:blockblast:allTests :feature:root:allTests :feature:home:allTests :feature:settings:allTests :core:data:allTests :composeApp:compileAndroidMain :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64`.
- [ ] Inspect the APK to confirm app review strings are packaged in the application resource namespace.
- [ ] Preserve generated SwiftPM and unrelated iOS-package changes outside the implementation diff.
