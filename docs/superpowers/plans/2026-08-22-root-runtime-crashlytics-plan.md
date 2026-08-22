# Root MiniApp Runtime and Crashlytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract MiniApp session runtime responsibilities from `DefaultRootComponent` and use the existing `CrashlyticsRepository` for session context, breadcrumbs, and caught launch failures.

**Architecture:** `DefaultRootComponent` remains the sole Decompose navigation and sheet owner. A new internal, non-Decompose `MiniAppRuntimeCoordinator` owns registry lookup, session keys, active visibility, bound hosts, stale-callback rejection, review reservation, and best-effort Crashlytics reporting while borrowing only the Running child's lifecycle-bound scope.

**Tech Stack:** Kotlin Multiplatform, Decompose 3.5, kotlinx.coroutines/serialization, Metro 1.4.1, Firebase Crashlytics facade, kotlin-test.

---

## File Map

- Create `feature/root/src/commonMain/kotlin/ge/yet/game/feature/root/MiniAppRuntimeCoordinator.kt`: internal runtime state machine, serializable session key, visibility source, bound host, and Crashlytics facade calls.
- Create `feature/root/src/commonTest/kotlin/ge/yet/game/feature/root/MiniAppRuntimeCoordinatorTest.kt`: focused behavior tests independent of Decompose navigation.
- Modify `feature/root/src/commonMain/kotlin/ge/yet/game/feature/root/DefaultRootComponent.kt`: delegate runtime work, retain navigation/sheets/lifecycle, and inject Crashlytics through its factory.
- Modify `feature/root/src/commonTest/kotlin/ge/yet/game/feature/root/DefaultRootComponentTest.kt`: provide recording Crashlytics and assert Root wiring/restoration.
- Modify `AGENTS.md`: document Root/runtime and Crashlytics ownership.

### Task 1: Specify the runtime coordinator contract with failing tests

**Files:**
- Create: `feature/root/src/commonTest/kotlin/ge/yet/game/feature/root/MiniAppRuntimeCoordinatorTest.kt`

- [ ] **Step 1: Add focused test fixtures**

Create private recording implementations for `MiniAppRegistry`, `MiniAppPlugin`,
`MiniAppSession`, `AppReviewPolicy`, `AnalyticRepository`, and
`CrashlyticsRepository`. The Crashlytics fixture records ordered operations:

```kotlin
private sealed interface CrashOperation {
    data class Value(val key: String, val value: Any) : CrashOperation
    data class Message(val value: String) : CrashOperation
    data class Exception(val value: Throwable) : CrashOperation
}

private class RecordingCrashlytics : CrashlyticsRepository {
    val operations = mutableListOf<CrashOperation>()
    override fun setCustomValue(key: String, value: Any) {
        operations += CrashOperation.Value(key, value)
    }
    override fun logMessage(message: String) {
        operations += CrashOperation.Message(message)
    }
    override fun logException(throwable: Throwable) {
        operations += CrashOperation.Exception(throwable)
    }
    override fun setUserID(id: String) = Unit
    override fun clearUserID() = Unit
}
```

- [ ] **Step 2: Write coordinator behavior tests**

Add tests named:

```kotlin
@Test fun launch_and_session_creation_publish_context_and_breadcrumbs()
@Test fun missing_plugin_logs_analytics_and_crash_breadcrumb_without_navigating()
@Test fun factory_failure_returns_unavailable_and_records_original_exception()
@Test fun visibility_changes_update_active_source_and_crash_context_without_recreation()
@Test fun active_close_is_delivered_once_and_stale_close_is_ignored()
@Test fun active_review_reserves_and_opens_sheet_while_stale_review_is_ignored()
@Test fun restored_key_advances_generator_and_old_callbacks_cannot_mutate_new_context()
@Test fun crashlytics_facade_failure_cannot_break_session_creation_or_navigation()
@Test fun cancellation_from_session_factory_is_rethrown_and_not_reported()
```

Use `LifecycleRegistry`, `DefaultComponentContext`, `runTest`, and `runCurrent`.
Assert exact `mini_app_id`, `mini_app_session_key`, `mini_app_visibility`, and
`mini_app_state` values plus breadcrumb ordering. Assert stale callbacks add no
new Crashlytics operation.

- [ ] **Step 3: Run the focused test and verify RED**

Run:

```bash
./gradlew :feature:root:compileTestKotlinIosSimulatorArm64
```

Expected: compilation fails only because `MiniAppRuntimeCoordinator` and
`MiniAppSessionKey` do not exist.

### Task 2: Implement the runtime coordinator

**Files:**
- Create: `feature/root/src/commonMain/kotlin/ge/yet/game/feature/root/MiniAppRuntimeCoordinator.kt`
- Test: `feature/root/src/commonTest/kotlin/ge/yet/game/feature/root/MiniAppRuntimeCoordinatorTest.kt`

- [ ] **Step 1: Add the serializable key and coordinator inputs**

```kotlin
@Serializable
@JvmInline
internal value class MiniAppSessionKey(val value: Long)

internal class MiniAppRuntimeCoordinator(
    private val registry: MiniAppRegistry,
    private val reviewPolicy: AppReviewPolicy,
    private val analytics: AnalyticRepository,
    private val crashlytics: CrashlyticsRepository,
    initialForeground: Boolean,
    private val closeActiveSession: () -> Unit,
    private val showReview: (MiniAppId, MiniAppReviewOpportunity) -> Boolean,
)
```

The class stores no `CoroutineScope`. It owns the last/pending/active session
keys, pending plugin, active visibility source, foreground state, and obscured
state.

- [ ] **Step 2: Implement guarded launch preparation**

Expose:

```kotlin
fun launch(id: MiniAppId, navigate: (MiniAppSessionKey) -> Unit)
```

Reject re-entrant launch and launch while a session is active. Resolve the
plugin once, emit existing `miniapp_launch_missing` analytics for an unknown ID,
and only invoke `navigate` for a resolved plugin. Hold the plugin as a pending
pair during synchronous Decompose child creation, then clear it in `finally`.

- [ ] **Step 3: Implement Running-child creation**

Expose:

```kotlin
fun createSession(
    id: MiniAppId,
    key: MiniAppSessionKey,
    componentContext: ComponentContext,
): RootComponent.MiniAppState
```

Advance the key generator, create the child-scoped visibility source and bound
host, initialize visibility synchronously, and clear active references from
`componentContext.lifecycle.doOnDestroy` only when the destroyed key is still
active. Resolve a pending plugin first and registry lookup second.

Call `plugin.createSession(componentContext, visibility, host)`, arm the host
only after success, and return `MiniAppState.Content`. Rethrow
`CancellationException`. For any other `Throwable`, preserve the existing
`miniapp_launch_failed` analytics event, record the original exception, and
return `MiniAppState.Unavailable`.

- [ ] **Step 4: Implement visibility projection**

Expose:

```kotlin
fun setForeground(value: Boolean)
fun setObscured(value: Boolean)
```

Project state with this exact priority:

```kotlin
when {
    !isForeground -> MiniAppVisibility.BACKGROUND
    isObscured -> MiniAppVisibility.OBSCURED
    else -> MiniAppVisibility.ACTIVE
}
```

Update only the active source. Emit `miniapp_visibility_changed` and update
`mini_app_visibility` only when the projected value actually changes.

- [ ] **Step 5: Implement the bound host and review reservation**

The private host receives the Running child's `CoroutineScope`. `close()` and
`requestReview()` launch only into that scope and return until armed. Check the
session key before every external effect. Review handling performs
`tryAcquirePrompt()`, checks key/obscured state again after suspension, calls
`showReview`, and releases the reservation in `NonCancellable` when acquisition
did not commit. Stale callbacks must not emit breadcrumbs or alter custom keys.

- [ ] **Step 6: Implement best-effort Crashlytics calls**

Use a private non-suspending helper that catches `Exception` thrown by the
Crashlytics facade. Do not catch `CancellationException` from session creation
and do not turn normal control flow into exceptions. Breadcrumb messages include
the ID and key, for example:

```text
miniapp_session_created id=game.blockblast key=1
miniapp_visibility_changed id=game.blockblast key=1 visibility=OBSCURED
```

On active close, emit the close breadcrumb before Root navigation destroys the
child. On destruction, clear custom context with empty ID/key/visibility values
and `mini_app_state=closed`; never clear a newer session's context.

- [ ] **Step 7: Run coordinator tests and verify GREEN**

```bash
./gradlew :feature:root:iosSimulatorArm64Test --tests '*MiniAppRuntimeCoordinatorTest'
```

Expected: all coordinator tests pass with zero failures.

- [ ] **Step 8: Commit the coordinator**

```bash
git add feature/root/src/commonMain/kotlin/ge/yet/game/feature/root/MiniAppRuntimeCoordinator.kt \
  feature/root/src/commonTest/kotlin/ge/yet/game/feature/root/MiniAppRuntimeCoordinatorTest.kt
git commit -m "feat: add mini-app runtime coordinator"
```

### Task 3: Delegate Root runtime behavior

**Files:**
- Modify: `feature/root/src/commonMain/kotlin/ge/yet/game/feature/root/DefaultRootComponent.kt`
- Modify: `feature/root/src/commonTest/kotlin/ge/yet/game/feature/root/DefaultRootComponentTest.kt`

- [ ] **Step 1: Extend the existing Root tests with Crashlytics wiring assertions**

Add `RecordingCrashlytics` to the Root test `Setup` and build helper. Strengthen
the existing factory-failure test to assert the exact original exception is
recorded. Strengthen restoration/stale-host tests to snapshot operations before
stale callbacks and assert no later mutation. Add one test:

```kotlin
@Test
fun root_lifecycle_and_sheet_changes_are_delegated_to_runtime_visibility_and_crash_context()
```

It launches once, drives resume/settings/background/foreground, and asserts the
same session instance plus ACTIVE/OBSCURED/BACKGROUND context transitions.

- [ ] **Step 2: Run Root tests and verify RED**

```bash
./gradlew :feature:root:iosSimulatorArm64Test --tests '*DefaultRootComponentTest'
```

Expected: compilation fails because Root/factory do not accept
`CrashlyticsRepository`, or the new assertions fail because Root has not yet
delegated runtime state.

- [ ] **Step 3: Wire the coordinator into `DefaultRootComponent`**

Add `CrashlyticsRepository` to `DefaultRootComponent` and
`DefaultRootComponentFactory`. Construct one coordinator with callbacks that:

```kotlin
closeActiveSession = {
    if (sheetSlot.value.child != null) sheetNavigation.dismiss()
    navigation.replaceAll(Config.Catalog)
}
showReview = { id, opportunity ->
    if (sheetSlot.value.child != null) false
    else {
        sheetNavigation.activate(
            SheetConfig.AppReview(
                miniAppId = id.value,
                source = opportunity.triggerId,
                score = opportunity.score,
                bestScore = opportunity.bestScore,
                revivesUsed = opportunity.revivesUsed,
            ),
        )
        true
    }
}
```

Move `SessionKey` out of Root and change `Config.Running.key` to
`MiniAppSessionKey`; keep `Config.serializer()` and `SheetConfig.serializer()`.

- [ ] **Step 4: Remove migrated Root responsibilities**

Delete Root's `lastSessionKey`, `playInProgress`, `pendingPlugin`, foreground /
obscured / active visibility fields, `BoundMiniAppSessionHost`, visibility-source
implementation, session creation error handling, review reservation block, and
visibility projection helpers. Replace them with coordinator calls from:

- `launchMiniApp`;
- `createRunningChild`;
- sheet subscription;
- lifecycle start/stop.

Keep child-stack construction, sheet child construction, Back handling, audio
lifecycle calls, and serialized configs in Root.

- [ ] **Step 5: Run the full Root test suite**

```bash
./gradlew :feature:root:allTests
```

Expected: all existing and new Root/coordinator tests pass. In particular,
restoration, stale callbacks, review reservation/release, and visibility do not
change behavior.

- [ ] **Step 6: Compile both platform final graphs**

```bash
./gradlew :composeApp:compileAndroidMain :composeApp:compileKotlinIosSimulatorArm64
```

Expected: Metro resolves the existing app-scoped `CrashlyticsRepository` binding
into `DefaultRootComponentFactory` on Android and iOS.

- [ ] **Step 7: Commit Root delegation**

```bash
git add feature/root/src/commonMain/kotlin/ge/yet/game/feature/root/DefaultRootComponent.kt \
  feature/root/src/commonTest/kotlin/ge/yet/game/feature/root/DefaultRootComponentTest.kt
git commit -m "refactor: delegate mini-app runtime from root"
```

### Task 4: Documentation and final verification

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1: Update the canonical architecture guide**

Document that Root owns Decompose navigation/sheets while its internal runtime
coordinator owns session keys, visibility, stale callbacks, and MiniApp
Crashlytics context. State that caught synchronous plugin-creation errors are
reported as non-fatal and normal lifecycle actions remain breadcrumbs.

- [ ] **Step 2: Run fresh affected tests and platform gates**

```bash
./gradlew :feature:root:allTests \
  :miniapp:integration-test:allTests \
  :composeApp:allTests \
  :composeApp:compileAndroidMain \
  :composeApp:linkDebugFrameworkIosSimulatorArm64 \
  --rerun-tasks
```

Expected: BUILD SUCCESSFUL and zero failed tests.

- [ ] **Step 3: Run structural checks**

```bash
git diff --check
rg -n "BoundMiniAppSessionHost|DefaultMiniAppVisibilitySource|lastSessionKey|pendingPlugin" \
  feature/root/src/commonMain/kotlin/ge/yet/game/feature/root/DefaultRootComponent.kt
```

Expected: `git diff --check` has no output and the `rg` scan finds none of the
migrated runtime implementation in `DefaultRootComponent`.

- [ ] **Step 4: Commit documentation**

```bash
git add AGENTS.md
git commit -m "docs: document mini-app runtime diagnostics"
```

- [ ] **Step 5: Confirm final repository state**

```bash
git status --short
git log -5 --oneline
```

Expected: clean working tree and the Task 1-4 commits visible on
`codex/miniapp-plugin-framework`.
