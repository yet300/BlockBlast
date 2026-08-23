# MiniApp Storage and Game-Data Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add namespaced typed MiniApp persistence, migrate Block Blast away from raw Settings, and let Settings safely delete all shipped MiniApp data after closing the active session.

**Architecture:** `:miniapp:api` owns storage/reset contracts, while a new app-infrastructure module `:miniapp:storage` owns the Multiplatform Settings backend, legacy-key cleanup and best-effort reset coordinator. Root supplies one `MiniAppSessionContext` to plugins and serializes reset against session launch/teardown. Settings owns confirmation/progress/result UI but receives the destructive operation as a suspending host callback.

**Tech Stack:** Kotlin Multiplatform, kotlinx.coroutines/Flow, kotlinx.serialization JSON, Multiplatform Settings, Metro DI, Decompose, Compose Multiplatform, kotlin-test.

---

## File Structure

- `miniapp/api/.../MiniAppStorage.kt`: storage/provider contracts and primitive operations.
- `miniapp/api/.../MiniAppSnapshot.kt`: versioned JSON snapshot specification and migration contract.
- `miniapp/api/.../MiniAppDataReset.kt`: reset result, legacy-key and additional-cleaner contracts.
- `miniapp/storage/.../SettingsBackedMiniAppStorage.kt`: one namespace-bound backend.
- `miniapp/storage/.../DefaultMiniAppStorageProvider.kt`: cached app-scoped storage factory with legacy local-name mappings.
- `miniapp/storage/.../DefaultMiniAppDataResetter.kt`: best-effort namespace/legacy/additional cleanup.
- `miniapp/storage/.../MiniAppStorageBindings.kt`: Metro app-scope bindings and empty multibindings.
- `miniapp/compose/.../MiniAppSessionContext.kt`: one typed runtime input for every plugin.
- `miniapp/metro/.../MiniAppSessionContextBindings.kt`: exposes context fields inside child graphs.
- `feature/root/.../MiniAppRuntimeCoordinator.kt`: session-context creation and reset/launch serialization.
- `feature/settings/.../reset/*`: Decompose state holder for confirmation/progress/result.
- `composeApp/.../ResetGameDataContent.kt`: destructive confirmation and result UI.

### Task 1: Stable Storage and Reset Contracts

**Files:**
- Modify: `miniapp/api/src/commonMain/kotlin/ge/yet/game/miniapp/api/MiniAppStorageKey.kt`
- Create: `miniapp/api/src/commonMain/kotlin/ge/yet/game/miniapp/api/MiniAppStorage.kt`
- Create: `miniapp/api/src/commonMain/kotlin/ge/yet/game/miniapp/api/MiniAppSnapshot.kt`
- Create: `miniapp/api/src/commonMain/kotlin/ge/yet/game/miniapp/api/MiniAppDataReset.kt`
- Create: `miniapp/api/src/commonTest/kotlin/ge/yet/game/miniapp/api/MiniAppStorageContractTest.kt`

- [ ] **Step 1: Write the failing contract tests**

Cover the wished-for API: primitive reads/writes/flows use local snake-case
names, snapshots carry a positive schema version, reset results expose only
failed MiniApp IDs, and legacy declarations require valid local names,
nonblank physical keys and one owner ID.

```kotlin
@Test
fun `snapshot specs require a positive current version`() {
    assertFailsWith<IllegalArgumentException> {
        MiniAppSnapshotSpec(String.serializer(), currentVersion = 0)
    }
}

@Test
fun `partial reset result contains stable failed ids only`() {
    val result = MiniAppDataResetResult.PartialFailure(
        failedMiniAppIds = setOf(MiniAppId("game.snake")),
    )
    assertEquals(setOf(MiniAppId("game.snake")), result.failedMiniAppIds)
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew :miniapp:api:allTests`

Expected: test compilation fails only because `MiniAppStorage`,
`MiniAppSnapshotSpec`, `MiniAppDataResetter` and reset declarations do not yet
exist.

- [ ] **Step 3: Add the minimal public contracts**

Use suspending operations and cold `Flow` observation so repositories do not
own hidden coroutine scopes:

```kotlin
interface MiniAppStorage {
    suspend fun getBoolean(localName: String, defaultValue: Boolean = false): Boolean
    suspend fun putBoolean(localName: String, value: Boolean)
    fun observeBoolean(localName: String, defaultValue: Boolean = false): Flow<Boolean>
    suspend fun getInt(localName: String, defaultValue: Int = 0): Int
    suspend fun putInt(localName: String, value: Int)
    fun observeInt(localName: String, defaultValue: Int = 0): Flow<Int>
    suspend fun getLong(localName: String, defaultValue: Long = 0L): Long
    suspend fun putLong(localName: String, value: Long)
    fun observeLong(localName: String, defaultValue: Long = 0L): Flow<Long>
    suspend fun getFloat(localName: String, defaultValue: Float = 0f): Float
    suspend fun putFloat(localName: String, value: Float)
    fun observeFloat(localName: String, defaultValue: Float = 0f): Flow<Float>
    suspend fun getDouble(localName: String, defaultValue: Double = 0.0): Double
    suspend fun putDouble(localName: String, value: Double)
    fun observeDouble(localName: String, defaultValue: Double = 0.0): Flow<Double>
    suspend fun getString(localName: String, defaultValue: String = ""): String
    suspend fun putString(localName: String, value: String)
    fun observeString(localName: String, defaultValue: String = ""): Flow<String>
    suspend fun remove(localName: String)
    suspend fun <T> readSnapshot(localName: String, spec: MiniAppSnapshotSpec<T>): T?
    suspend fun <T> writeSnapshot(localName: String, value: T, spec: MiniAppSnapshotSpec<T>)
}

fun interface MiniAppStorageProvider {
    fun storageFor(id: MiniAppId): MiniAppStorage
}

interface MiniAppDataResetter {
    suspend fun clear(miniAppIds: Set<MiniAppId>): MiniAppDataResetResult
}
```

`MiniAppSnapshotSpec<T>` stores a serializer, positive current version and a
map from source version to pure `JsonElement -> JsonElement` migration. Reset
results are `Success` or `PartialFailure(failedMiniAppIds)`; raw exceptions are
not part of the UI contract.

- [ ] **Step 4: Run GREEN verification**

Run: `./gradlew :miniapp:api:allTests :miniapp:api:compileAndroidMain :miniapp:api:compileKotlinIosSimulatorArm64`

Expected: all contract tests and both platform compilations pass.

- [ ] **Step 5: Commit**

```bash
git add miniapp/api
git commit -m "feat: define mini-app storage contracts"
```

### Task 2: Settings-Backed Storage Module

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `composeApp/build.gradle.kts`
- Create: `miniapp/storage/build.gradle.kts`
- Create: `miniapp/storage/src/commonMain/kotlin/ge/yet/game/miniapp/storage/SettingsBackedMiniAppStorage.kt`
- Create: `miniapp/storage/src/commonMain/kotlin/ge/yet/game/miniapp/storage/DefaultMiniAppStorageProvider.kt`
- Create: `miniapp/storage/src/commonMain/kotlin/ge/yet/game/miniapp/storage/StoredMiniAppSnapshot.kt`
- Create: `miniapp/storage/src/commonMain/kotlin/ge/yet/game/miniapp/storage/di/MiniAppStorageBindings.kt`
- Create: `miniapp/storage/src/commonTest/kotlin/ge/yet/game/miniapp/storage/SettingsBackedMiniAppStorageTest.kt`

- [ ] **Step 1: Add only the module/build fixture and failing backend tests**

The tests use `MapSettings`, `makeObservable()` and unconfined test dispatchers.
They prove namespace isolation, every primitive type, validation before I/O,
reactive defaults after removal, snapshot round trip, chained migration,
missing migration rejection and unreadable-envelope removal.
They also prove that a contributed local-to-physical legacy mapping keeps an
existing key unchanged rather than silently migrating it.

```kotlin
@Test
fun `two storages cannot read each others local key`() = runTest {
    val settings = MapSettings()
    val snake = storage(settings, MiniAppId("game.snake"))
    val blocks = storage(settings, MiniAppId("game.blocks"))
    snake.putLong("best_score", 42)
    assertEquals(42, snake.getLong("best_score"))
    assertEquals(0, blocks.getLong("best_score"))
}
```

- [ ] **Step 2: Run RED**

Run: `./gradlew :miniapp:storage:allTests`

Expected: compilation fails on missing backend/provider classes, after the new
module itself configures successfully.

- [ ] **Step 3: Implement the namespace-bound backend**

Every operation derives the physical key with
`miniAppId.storageKey(localName).value`, switches storage I/O through
`AppDispatchers.io`, and serializes compound snapshot operations with a
`Mutex`. Observation delegates to cold Multiplatform Settings coroutine flows;
it must not create or store a scope.

Snapshots use this internal envelope:

```kotlin
@Serializable
internal data class StoredMiniAppSnapshot(
    val version: Int,
    val payload: JsonElement,
)
```

Apply migrations in ascending versions until `currentVersion`; reject a gap.
Remove malformed or unsupported blobs so subsequent reads do not repeatedly
parse a permanent trap.

- [ ] **Step 4: Bind one cached provider**

`DefaultMiniAppStorageProvider` validates the ID and returns the same
`MiniAppStorage` instance for repeated requests. Protect its map with a lock;
do not launch work from construction. It receives the empty-capable set of
`MiniAppLegacyStorageKeys`, rejects duplicate `(id, localName)` mappings, and
passes the mapping into each backend. Bind it as both implementation provider
and reset backend dependency in `MiniAppStorageBindings` with
`@SingleIn(AppScope::class)`. Add `:miniapp:storage` to `composeApp` so final
Android/iOS graphs aggregate the implementation without exposing it to games.

- [ ] **Step 5: Run GREEN verification**

Run: `./gradlew :miniapp:storage:allTests :miniapp:storage:compileAndroidMain :miniapp:storage:compileKotlinIosSimulatorArm64`

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts composeApp/build.gradle.kts miniapp/storage
git commit -m "feat: add namespaced mini-app storage backend"
```

### Task 3: One Typed MiniApp Session Context

**Files:**
- Modify: `miniapp/compose/src/commonMain/kotlin/ge/yet/game/miniapp/compose/MiniAppPlugin.kt`
- Create: `miniapp/compose/src/commonMain/kotlin/ge/yet/game/miniapp/compose/MiniAppSessionContext.kt`
- Create: `miniapp/metro/src/commonMain/kotlin/ge/yet/game/miniapp/metro/MiniAppSessionContextBindings.kt`
- Modify: `feature/root/build.gradle.kts`
- Modify: `feature/root/src/commonMain/kotlin/ge/yet/game/feature/root/MiniAppRuntimeCoordinator.kt`
- Modify: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/BlockBlastPlugin.kt`
- Modify: `miniapp/samples/counter/src/commonMain/kotlin/ge/yet/sample/counter/CounterPlugin.kt`
- Modify: `feature/root/src/commonTest/kotlin/ge/yet/game/feature/root/MiniAppRuntimeCoordinatorTest.kt`
- Modify: `feature/root/src/commonTest/kotlin/ge/yet/game/feature/root/DefaultRootComponentTest.kt`
- Modify: `feature/catalog/src/commonTest/kotlin/ge/yet/game/feature/catalog/DefaultCatalogComponentTest.kt`
- Modify: `miniapp/compose/src/commonTest/kotlin/ge/yet/game/miniapp/compose/MiniAppContractsTest.kt`
- Modify: `miniapp/testkit/src/commonTest/kotlin/ge/yet/game/miniapp/testkit/MiniAppContractAssertionsTest.kt`
- Modify: `miniapp/integration-test/src/commonTest/kotlin/ge/yet/game/miniapp/integration/CounterRootHarness.kt`
- Modify: `game/blockblast/src/commonTest/kotlin/ge/yet/game/blockblast/BlockBlastPluginContractTest.kt`
- Modify: `game/blockblast/src/commonTest/kotlin/ge/yet/game/blockblast/di/BlockBlastSessionGraphTest.kt`
- Modify: `miniapp/samples/counter/src/commonTest/kotlin/ge/yet/sample/counter/CounterPluginContractTest.kt`
- Modify: `miniapp/integration-test/src/commonTest/kotlin/ge/yet/game/miniapp/integration/CounterRegistryIntegrationTest.kt`
- Modify: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/di/BlockBlastSessionGraph.kt`
- Modify: `miniapp/samples/counter/src/commonMain/kotlin/ge/yet/sample/counter/CounterSessionGraph.kt`
- Modify: `build-logic/convention/src/main/kotlin/com/yet/plugins/miniapp/MiniAppScaffoldRenderer.kt`
- Modify matching contract/scaffold/integration tests.

- [ ] **Step 1: Add failing context contract and scaffold tests**

Assert that `MiniAppPlugin.createSession` accepts exactly one context, that
the same storage returned for the manifest ID reaches the plugin, and that a
generated child graph factory accepts one namespaced method parameter:

```kotlin
override fun createSession(context: MiniAppSessionContext): MiniAppSession
```

- [ ] **Step 2: Run RED**

Run: `./gradlew :miniapp:compose:allTests :feature:root:allTests -p build-logic :convention:test --tests '*CreateMiniAppTaskTest'`

Run these as separate Gradle invocations if `-p build-logic` cannot share the
root task graph. Expected failure is the absent context/signature, not fixture
configuration.

- [ ] **Step 3: Define and bind the context**

```kotlin
interface MiniAppSessionContext {
    val componentContext: ComponentContext
    val visibility: MiniAppVisibilitySource
    val host: MiniAppSessionHost
    val storage: MiniAppStorage
}
```

Root creates an immutable implementation using
`storageProvider.storageFor(plugin.manifest.id)`. A
`MiniAppSessionContextBindings` container contributed to
`MiniAppSessionScope` provides the four typed fields from the one runtime
input. Child graph factories accept only `@Provides context:
MiniAppSessionContext`; game-internal providers can continue requesting
`ComponentContext`, visibility, host or storage directly.

- [ ] **Step 4: Migrate every plugin, fake, final graph and scaffold**

Keep namespaced graph-factory method names. Counter and Block Blast call their
factory with the single context. Update generated contract tests so future
contributors receive the same API automatically.

- [ ] **Step 5: Verify GREEN across framework and both plugins**

Run:

```bash
./gradlew :miniapp:compose:allTests :miniapp:metro:allTests \
  :feature:root:allTests :game:blockblast:allTests \
  :miniapp:samples:counter:allTests :miniapp:integration-test:allTests
./gradlew -p build-logic :convention:test --tests '*CreateMiniAppTaskTest'
```

- [ ] **Step 6: Commit**

```bash
git add miniapp game/blockblast feature/root build-logic/convention
git commit -m "refactor: pass one mini-app session context"
```

### Task 4: Migrate Block Blast Persistence

**Files:**
- Modify: `game/blockblast/build.gradle.kts`
- Modify: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/di/BlockBlastAppBindings.kt`
- Modify: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/data/repository/SettingsBackedGameSaveRepository.kt`
- Modify: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/data/repository/SettingsBackedBestScoreRepository.kt`
- Modify: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/data/repository/SettingsBackedBlockBlastTutorialRepository.kt`
- Modify their three common tests and graph tests.

- [ ] **Step 1: Rewrite repository tests against `MiniAppStorage` first**

Use a real `SettingsBackedMiniAppStorage` test fixture and seed the three
legacy physical keys. Add a warm-state reset regression: after writing and
observing a value, clearing legacy/namespace data causes best score/tutorial
observers to emit defaults and a subsequent save load returns null.

- [ ] **Step 2: Run RED**

Run: `./gradlew :game:blockblast:allTests`

Expected: constructors still require raw `Settings`/`ObservableSettings` and
cannot satisfy the storage-backed tests.

- [ ] **Step 3: Replace raw Settings dependencies**

Bind `MiniAppStorageProvider.storageFor(MiniAppId("game.blockblast"))` once in
`BlockBlastAppBindings`. Contribute one `MiniAppLegacyStorageKeys` mapping from
`game_save`, `best_score` and `tutorial_seen` to the three existing physical
keys. Repositories request `MiniAppStorage`, so existing installations continue
reading and writing the unchanged legacy keys while new local names use the
normal namespace.
Remove repository-local caches that can survive external reset; use storage
flows for best score and tutorial state.

- [ ] **Step 4: Remove forbidden external storage dependencies**

Delete direct `multiplatform-settings` and settings-coroutines dependencies
from `game/blockblast/build.gradle.kts`. Add a convention functional test that
rejects direct `com.russhwolf:multiplatform-settings*` dependencies from any
module applying `logica.miniapp`.

- [ ] **Step 5: Verify GREEN and dependency boundary**

Run:

```bash
./gradlew :game:blockblast:allTests :game:blockblast:validateMiniAppDependencies
./gradlew :game:blockblast:dependencies --configuration commonMainResolvableDependenciesMetadata
./gradlew -p build-logic :convention:test --tests '*MiniAppDependencyBoundaryTest' --tests '*MiniAppConventionPluginTest'
```

The dependency report must contain MiniApp contracts/storage but no
Multiplatform Settings artifact on Block Blast's direct declared boundary.

- [ ] **Step 6: Commit**

```bash
git add game/blockblast build-logic/convention
git commit -m "refactor: move block blast persistence behind mini-app storage"
```

### Task 5: Best-Effort Reset Coordinator and Legacy Aliases

**Files:**
- Create: `miniapp/storage/src/commonMain/kotlin/ge/yet/game/miniapp/storage/DefaultMiniAppDataResetter.kt`
- Modify: `miniapp/storage/src/commonMain/kotlin/ge/yet/game/miniapp/storage/di/MiniAppStorageBindings.kt`
- Create: `miniapp/storage/src/commonTest/kotlin/ge/yet/game/miniapp/storage/DefaultMiniAppDataResetterTest.kt`
- Create: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/data/BlockBlastLegacyStorageKeys.kt`
- Create/modify Block Blast contribution tests.

- [ ] **Step 1: Write failing reset tests**

Prove namespace-only removal preserves host keys, all three Block Blast legacy
aliases are removed, cleaners run independently, failures are aggregated by ID,
cancellation is rethrown, Crashlytics receives only failed IDs, and repeated
reset is successful.

- [ ] **Step 2: Run RED**

Run: `./gradlew :miniapp:storage:allTests :game:blockblast:allTests`

- [ ] **Step 3: Implement reset and empty multibindings**

`DefaultMiniAppDataResetter.clear(ids)` validates/sorts a defensive ID snapshot,
removes keys whose physical prefix matches each ID, removes matching contributed
legacy aliases, then invokes matching additional cleaners. Catch ordinary
failures per MiniApp, rethrow `CancellationException`, continue unrelated IDs
and report/log the final failed set.

Reuse the empty-capable `MiniAppLegacyStorageKeys` set from Task 2 and declare
an empty-capable `MiniAppAdditionalDataCleaner` set. Block Blast already
contributes only its legacy-key declaration. New games need no contribution
unless they have exceptional data.

- [ ] **Step 4: Verify GREEN on Android and iOS final graphs**

Run:

```bash
./gradlew :miniapp:storage:allTests :game:blockblast:allTests \
  :composeApp:compileAndroidMain :composeApp:compileKotlinIosSimulatorArm64
```

- [ ] **Step 5: Commit**

```bash
git add miniapp/storage game/blockblast
git commit -m "feat: clear mini-app data with legacy aliases"
```

### Task 6: Settings Confirmation and Result State

**Files:**
- Modify: `feature/settings/build.gradle.kts`
- Modify: `feature/settings/src/commonMain/kotlin/ge/yet/game/feature/settings/SettingsComponent.kt`
- Modify: `feature/settings/src/commonMain/kotlin/ge/yet/game/feature/settings/DefaultSettingsComponent.kt`
- Modify: `feature/settings/src/commonMain/kotlin/ge/yet/game/feature/settings/more/MoreSettingsComponent.kt`
- Modify: `feature/settings/src/commonMain/kotlin/ge/yet/game/feature/settings/more/DefaultMoreSettingsComponent.kt`
- Create: `feature/settings/src/commonMain/kotlin/ge/yet/game/feature/settings/reset/ResetGameDataComponent.kt`
- Create: `feature/settings/src/commonMain/kotlin/ge/yet/game/feature/settings/reset/DefaultResetGameDataComponent.kt`
- Create matching common tests.

- [ ] **Step 1: Write failing Decompose component tests**

Tests prove More opens exactly one Reset child, Back before confirmation performs
no deletion, confirm calls the suspending host operation once, repeated taps
while clearing are ignored, success and partial failure are explicit model
states, retry invokes the operation again, and child destruction cancels work.

- [ ] **Step 2: Run RED**

Run: `./gradlew :feature:settings:allTests`

- [ ] **Step 3: Implement the component state machine**

```kotlin
sealed interface ResetGameDataComponent.Status {
    data object Confirming : Status
    data object Clearing : Status
    data object Success : Status
    data class PartialFailure(val failedMiniAppIds: Set<MiniAppId>) : Status
}
```

The component is the UI/state-holder boundary: `onConfirmClicked()` launches
the supplied suspend operation on its lifecycle-bound component scope. It
rethrows cancellation and maps only the typed reset result into `Value<Model>`.

- [ ] **Step 4: Verify GREEN**

Run: `./gradlew :feature:settings:allTests`

- [ ] **Step 5: Commit**

```bash
git add feature/settings
git commit -m "feat: add game-data reset settings flow"
```

### Task 7: Root Teardown Ordering and Launch Serialization

**Files:**
- Modify: `feature/root/build.gradle.kts`
- Modify: `feature/root/src/commonMain/kotlin/ge/yet/game/feature/root/DefaultRootComponent.kt`
- Modify: `feature/root/src/commonMain/kotlin/ge/yet/game/feature/root/MiniAppRuntimeCoordinator.kt`
- Modify: `feature/root/src/commonTest/kotlin/ge/yet/game/feature/root/DefaultRootComponentTest.kt`
- Modify: `feature/root/src/commonTest/kotlin/ge/yet/game/feature/root/MiniAppRuntimeCoordinatorTest.kt`
- Modify Root/integration graph fixtures for new injected dependencies.

- [ ] **Step 1: Add failing ordering and race tests**

Prove:

1. reset requested over Running+Settings navigates to Catalog;
2. the child lifecycle is destroyed before `MiniAppDataResetter.clear` starts;
3. the Settings sheet remains to display progress/result;
4. a Catalog launch attempted during reset is ignored;
5. reset cancellation clears the in-progress guard and allows later launch;
6. partial failure is returned unchanged to Settings;
7. a stale session close cannot interfere after reset.

- [ ] **Step 2: Run RED**

Run: `./gradlew :feature:root:allTests`

- [ ] **Step 3: Implement structured reset orchestration**

Inject `MiniAppStorageProvider` and `MiniAppDataResetter`. Session creation
builds `MiniAppSessionContext` from the active child context, visibility, bound
host and `storageFor(id)`. Reset takes a defensive shipped-ID snapshot from
`MiniAppRegistry.manifests`.

Coordinator exposes a suspending reset method that sets a main-confined
`resetInProgress` guard, navigates to Catalog, awaits the active child's
`doOnDestroy` completion when necessary, then calls the resetter. A `finally`
block always clears the guard. It owns no new long-lived scope.

- [ ] **Step 4: Run GREEN plus integration graphs**

Run:

```bash
./gradlew :feature:root:allTests :miniapp:integration-test:allTests \
  :miniapp:integration-test:testAndroidHostTest \
  :miniapp:integration-test:iosSimulatorArm64Test
```

- [ ] **Step 5: Commit**

```bash
git add feature/root miniapp/integration-test
git commit -m "feat: reset game data after session teardown"
```

### Task 8: Compose Settings UI, Localization and Documentation

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/ge/yet/game/screen/settings/SettingsContent.kt`
- Modify: `composeApp/src/commonMain/kotlin/ge/yet/game/screen/settings/content/MoreSettingsContent.kt`
- Create: `composeApp/src/commonMain/kotlin/ge/yet/game/screen/settings/content/ResetGameDataContent.kt`
- Modify base Compose resources and relevant UI tests.
- Modify: `AGENTS.md`
- Modify: `README.md`

- [ ] **Step 1: Write failing Compose behavior tests**

Verify a destructive row exists under More, confirmation explicitly distinguishes
game data from app preferences, progress disables destructive actions, success
has a Done action, partial failure lists stable MiniApp IDs and exposes Retry,
and system Back follows the nested Settings stack.

- [ ] **Step 2: Run RED**

Run: `./gradlew :composeApp:iosSimulatorArm64Test --tests '*SettingsContentTest'`

- [ ] **Step 3: Implement Material 3 UI and strings**

Keep logic outside composables. Use existing settings list/section components,
Material 3 destructive colors, minimum touch targets, semantics and
`stringResource`. Do not introduce a Dialog if the existing Settings child
stack can render the confirmation as a full settings page.

- [ ] **Step 4: Update architecture guidance**

Document `:miniapp:storage`, `MiniAppSessionContext`, the raw-Settings ban,
storage/reset verification commands and the rule that an active session is
destroyed before clearing data. Replace README advice to call
`MiniAppId.storageKey` directly with the session-bound storage API.
Do not create the independently designed human/AI contributor guides in this
storage plan.

- [ ] **Step 5: Run complete verification**

```bash
./gradlew :miniapp:api:allTests :miniapp:storage:allTests \
  :miniapp:compose:allTests :miniapp:metro:allTests \
  :game:blockblast:allTests :game:blockblast:validateMiniAppDependencies \
  :feature:settings:allTests :feature:root:allTests \
  :miniapp:samples:counter:allTests :miniapp:integration-test:allTests \
  :composeApp:allTests :composeApp:compileAndroidMain \
  :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64
./gradlew :miniapp:bundle:verifyMiniAppBundle
./gradlew -p build-logic :convention:test --tests '*MiniAppConventionPluginTest' \
  --tests '*MiniAppDependencyBoundaryTest' --tests '*CreateMiniAppTaskTest'
```

Also run:

```bash
rg -n 'com\.russhwolf\.settings' game miniapp/samples --glob '*.kt'
git diff --check
```

The source scan must return no production MiniApp matches. Test fixtures may
use `MapSettings` only in the framework storage module.

- [ ] **Step 6: Commit**

```bash
git add composeApp AGENTS.md README.md
git commit -m "feat: expose safe game-data reset in settings"
```

## Completion Criteria

- MiniApps receive one typed context and never raw Settings.
- All primitive and snapshot values are namespace-isolated.
- Block Blast retains compatibility with existing data and can be fully reset.
- Reset closes the active session before deletion and blocks concurrent launch.
- Host preferences, consent, entitlement and review data survive game reset.
- Partial failures are visible and diagnosable without exposing exceptions.
- Generated MiniApps compile with the same storage/session API.
- Android and iOS final graphs, tests and shipping bundle remain valid.
