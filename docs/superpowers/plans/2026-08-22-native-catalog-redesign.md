# Native MiniApp Catalog Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current catalog cards with a native Material 3 `ListItem` library, adaptive one/two-column layout, restored details dialog, and long-press Play/Details menu.

**Architecture:** `CatalogComponent` continues to snapshot the immutable registry and owns only durable details selection; the selected `MiniAppId` is serialized through Essenty `StateKeeper`. Compose owns transient context-menu anchoring, renders a transparent `Scaffold` with `CenterAlignedTopAppBar`, and delegates all durable actions through component callbacks. Haze 1.7.2 is confined to the app-bar backdrop.

**Tech Stack:** Kotlin Multiplatform 2.4, Compose Multiplatform 1.11.1, Material 3, Decompose/Essenty, kotlinx.serialization, Haze 1.7.2, kotlin-test, Compose UI tests.

---

## File Map

- Modify `gradle/libs.versions.toml`: add the Haze 1.7.2 version and library alias.
- Modify `feature/catalog/build.gradle.kts`: consume Haze and Compose UI test APIs.
- Modify `feature/catalog/src/commonMain/composeResources/values/strings.xml`: add app-bar, details, close, empty, and accessibility strings.
- Modify `feature/catalog/src/commonMain/kotlin/ge/yet/game/feature/catalog/CatalogComponent.kt`: expose immutable details state and callbacks.
- Modify `feature/catalog/src/commonMain/kotlin/ge/yet/game/feature/catalog/DefaultCatalogComponent.kt`: serialize/restore the selected ID and normalize stale IDs.
- Modify `feature/catalog/src/commonMain/kotlin/ge/yet/game/feature/catalog/PreviewCatalogComponent.kt`: satisfy the new component contract.
- Modify `feature/catalog/src/commonMain/kotlin/ge/yet/game/feature/catalog/ui/CatalogContent.kt`: screen-level subscription and adaptive catalog composition.
- Create `feature/catalog/src/commonMain/kotlin/ge/yet/game/feature/catalog/ui/CatalogLayoutPolicy.kt`: pure one/two-column and max-width policy.
- Create `feature/catalog/src/commonMain/kotlin/ge/yet/game/feature/catalog/ui/MiniAppListItemCard.kt`: Material Card + ListItem + context menu.
- Create `feature/catalog/src/commonMain/kotlin/ge/yet/game/feature/catalog/ui/MiniAppDetailsDialog.kt`: cover/no-cover dialog.
- Modify `feature/catalog/src/commonTest/kotlin/ge/yet/game/feature/catalog/DefaultCatalogComponentTest.kt`: state, restoration, and stale-ID tests.
- Replace `feature/catalog/src/commonTest/kotlin/ge/yet/game/feature/catalog/ui/CatalogLayoutPolicyTest.kt`: adaptive layout tests.
- Create `feature/catalog/src/commonTest/kotlin/ge/yet/game/feature/catalog/ui/CatalogContentTest.kt`: Compose interaction tests.
- Modify `AGENTS.md`: document native catalog interactions and deferred Share/deep links.

### Task 1: Component-owned restored details selection

**Files:**
- Modify: `feature/catalog/src/commonMain/kotlin/ge/yet/game/feature/catalog/CatalogComponent.kt`
- Modify: `feature/catalog/src/commonMain/kotlin/ge/yet/game/feature/catalog/DefaultCatalogComponent.kt`
- Modify: `feature/catalog/src/commonMain/kotlin/ge/yet/game/feature/catalog/PreviewCatalogComponent.kt`
- Test: `feature/catalog/src/commonTest/kotlin/ge/yet/game/feature/catalog/DefaultCatalogComponentTest.kt`

- [ ] **Step 1: Add failing component tests**

Add tests that call `onDetailsRequested`, `onDetailsDismissed`, save through
`StateKeeperDispatcher`, restore with `DefaultComponentContext(lifecycle,
stateKeeper)`, and assert:

```kotlin
assertEquals(beta, component.details.value)
assertEquals(null, component.details.value)
assertEquals(beta, restored.details.value)
assertEquals(null, restoredWithMissingManifest.details.value)
```

Keep the existing no-session-creation and registry-order assertions.

- [ ] **Step 2: Run the component tests and verify RED**

Run:

```bash
./gradlew :feature:catalog:allTests
```

Expected: test compilation fails because `details`, `onDetailsRequested`, and
`onDetailsDismissed` do not exist.

- [ ] **Step 3: Add the minimal public component contract**

Use this shape:

```kotlin
interface CatalogComponent {
    val model: Value<Model>
    val details: Value<MiniAppManifest?>

    fun onPlayClicked(id: MiniAppId)
    fun onDetailsRequested(id: MiniAppId)
    fun onDetailsDismissed()

    data class Model(val manifests: List<MiniAppManifest>)
    // existing Factory remains unchanged
}
```

- [ ] **Step 4: Implement serialized selection without registry lookup**

Snapshot manifests once, index that snapshot by ID, and serialize only the ID
with Essenty's mutable `saveable` delegate:

```kotlin
@Serializable
private data class SavedState(val detailsId: MiniAppId? = null)

private val manifests = registry.manifests.toList()
private val manifestsById = manifests.associateBy(MiniAppManifest::id)
private var savedState by stateKeeper.saveable(
    serializer = SavedState.serializer(),
    key = STATE_KEY,
    init = ::SavedState,
)
private val mutableDetails = MutableValue(savedState.detailsId?.let(manifestsById::get))
override val details: Value<MiniAppManifest?> = mutableDetails
```

`onDetailsRequested` must resolve only from `manifestsById`; unknown IDs close
details. Both callbacks update `savedState` and `mutableDetails` together.
`onDetailsDismissed` sets both to null. Opt in to
`ExperimentalStateKeeperApi`. Do not call `registry.get`.

- [ ] **Step 5: Make PreviewCatalogComponent expose a MutableValue details state**

The preview implements both callbacks and exposes `Value<MiniAppManifest?>`.

- [ ] **Step 6: Run tests and commit**

Run `./gradlew :feature:catalog:allTests`; expect PASS. Commit:

```bash
git add feature/catalog/src/commonMain feature/catalog/src/commonTest
git commit -m "feat: restore catalog app details selection"
```

### Task 2: Adaptive catalog policy and dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `feature/catalog/build.gradle.kts`
- Create: `feature/catalog/src/commonMain/kotlin/ge/yet/game/feature/catalog/ui/CatalogLayoutPolicy.kt`
- Modify: `feature/catalog/src/commonTest/kotlin/ge/yet/game/feature/catalog/ui/CatalogLayoutPolicyTest.kt`

- [ ] **Step 1: Replace the old padding-only test with failing adaptive tests**

Test exact boundaries:

```kotlin
assertEquals(1, catalogColumnCount(839.dp))
assertEquals(2, catalogColumnCount(840.dp))
assertEquals(2, catalogColumnCount(1600.dp))
assertEquals(16.dp, catalogOuterPadding(600.dp))
assertEquals(24.dp, catalogOuterPadding(840.dp))
assertEquals(1200.dp, catalogContentWidth(1600.dp))
```

Retain the safe-insets-added-once test.

- [ ] **Step 2: Run the focused test and verify RED**

Run `./gradlew :feature:catalog:allTests`; expect unresolved policy functions.

- [ ] **Step 3: Implement the pure policy**

```kotlin
internal val CatalogExpandedWidth = 840.dp
internal val CatalogMaxContentWidth = 1200.dp

internal fun catalogColumnCount(width: Dp): Int =
    if (width >= CatalogExpandedWidth) 2 else 1

internal fun catalogOuterPadding(width: Dp): Dp =
    if (width >= CatalogExpandedWidth) 24.dp else 16.dp

internal fun catalogContentWidth(width: Dp): Dp =
    minOf(width, CatalogMaxContentWidth)
```

- [ ] **Step 4: Add Haze 1.7.2 and UI test dependencies**

Add catalog aliases in `libs.versions.toml` and use:

```kotlin
commonMain.dependencies {
    implementation(libs.haze)
}
commonTest.dependencies {
    implementation(libs.bundles.testing)
    implementation(libs.compose.ui.test)
}
```

- [ ] **Step 5: Run tests/compilation and commit**

Run:

```bash
./gradlew :feature:catalog:allTests \
  :feature:catalog:compileAndroidMain \
  :feature:catalog:compileKotlinIosSimulatorArm64
```

Commit:

```bash
git add gradle/libs.versions.toml feature/catalog
git commit -m "build: add adaptive catalog and Haze support"
```

### Task 3: Material ListItem cards and context menu

**Files:**
- Create: `feature/catalog/src/commonMain/kotlin/ge/yet/game/feature/catalog/ui/MiniAppListItemCard.kt`
- Modify: `feature/catalog/src/commonMain/composeResources/values/strings.xml`
- Test: `feature/catalog/src/commonTest/kotlin/ge/yet/game/feature/catalog/ui/CatalogContentTest.kt`

- [ ] **Step 1: Write failing Compose tests for card semantics**

Render a plain card with deterministic resources and assert:

```kotlin
onNodeWithText("Block Blast").performClick()
assertEquals(id, detailsId)

onNodeWithTag("catalog_play_game.blockblast").performClick()
assertEquals(id, playedId)

onNodeWithTag("catalog_card_game.blockblast").performTouchInput { longClick() }
onNodeWithText("Play").assertIsDisplayed()
onNodeWithText("Details").assertIsDisplayed()
onNodeWithText("Share").assertDoesNotExist()
```

- [ ] **Step 2: Run the focused suite and verify RED**

Run `./gradlew :feature:catalog:iosSimulatorArm64Test --tests '*CatalogContentTest'`;
expect missing card/menu semantics.

- [ ] **Step 3: Implement the Card + ListItem primitive**

Create a composable with required parameters first and `modifier` last:

```kotlin
@Composable
internal fun MiniAppListItemCard(
    manifest: MiniAppManifest,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Use `Card` as the root, `combinedClickable(onClick = onDetails,
onLongClick = { menuExpanded = true })`, and exactly one `ListItem` with icon,
headline, supporting text, and trailing Play. Anchor a `DropdownMenu` to the card
with only Play and Details entries. Close the menu before invoking callbacks.
Do not add Share.

- [ ] **Step 4: Add localized catalog strings**

Add `app_name`, `play`, `details`, `close`, `catalog_empty_title`, and accessible
Play/Details labels. Use `Logica` as the app-bar title.

- [ ] **Step 5: Run focused tests and commit**

Run the focused iOS test and `:feature:catalog:allTests`; expect PASS. Commit:

```bash
git add feature/catalog
git commit -m "feat: add native mini-app list cards"
```

### Task 4: Restored details dialog

**Files:**
- Create: `feature/catalog/src/commonMain/kotlin/ge/yet/game/feature/catalog/ui/MiniAppDetailsDialog.kt`
- Modify: `feature/catalog/src/commonTest/kotlin/ge/yet/game/feature/catalog/ui/CatalogContentTest.kt`

- [ ] **Step 1: Add failing cover/no-cover and callback tests**

Assert that no-cover renders the icon and full description, cover renders its
resource, Play fires once, Close fires once, and no rating/version/developer
labels exist.

- [ ] **Step 2: Run focused test and verify RED**

Run the iOS `CatalogContentTest`; expect missing dialog symbols/semantics.

- [ ] **Step 3: Implement the dialog**

Use `Dialog(onDismissRequest)` with a width-constrained Material `Card`. Render
cover with `ContentScale.Crop` when present, otherwise the real icon on
`surfaceContainer`. Make dialog body vertically scrollable for large fonts.
Render only icon, title, complete description, Play, and Close.

- [ ] **Step 4: Run tests and commit**

Run catalog allTests and Android/iOS compilation; expect PASS. Commit:

```bash
git add feature/catalog
git commit -m "feat: add mini-app details dialog"
```

### Task 5: Centered Haze app bar and adaptive screen composition

**Files:**
- Modify: `feature/catalog/src/commonMain/kotlin/ge/yet/game/feature/catalog/ui/CatalogContent.kt`
- Modify: `feature/catalog/src/commonTest/kotlin/ge/yet/game/feature/catalog/ui/CatalogContentTest.kt`

- [ ] **Step 1: Add failing screen tests**

Assert `Logica` appears exactly once, no navigation/actions exist, empty state
has no Retry, clicking a card calls Details, and dialog selection from component
state renders. Add tagged one/two-column containers driven by fixed constraints.

- [ ] **Step 2: Run and verify RED**

Run the focused iOS suite; expect old `LazyColumn` layout failures.

- [ ] **Step 3: Compose the adaptive screen**

Use `BoxWithConstraints` to derive the pure policy once, a transparent
`Scaffold`, `CenterAlignedTopAppBar`, and `LazyVerticalGrid` with `GridCells.Fixed`
of one or two. Center the grid inside a maximum 1200 dp width. Attach
`hazeSource` to scrolling content and `hazeEffect` only to the transparent app
bar. Apply Scaffold/safe drawing insets once.

The screen-level overload subscribes to both `component.model` and
`component.details` and passes callbacks into a plain state/callback overload.

- [ ] **Step 4: Run catalog tests and compiles**

```bash
./gradlew :feature:catalog:allTests \
  :feature:catalog:compileAndroidMain \
  :feature:catalog:compileKotlinIosSimulatorArm64
```

- [ ] **Step 5: Commit**

```bash
git add feature/catalog
git commit -m "feat: redesign the adaptive mini-app catalog"
```

### Task 6: Integration verification and canonical docs

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1: Update AGENTS**

Document the native ListItem catalog, details restoration, long-press Play/
Details menu, one/two-column policy, and that Share remains deferred until a
MiniApp deep-link API exists.

- [ ] **Step 2: Run the affected vertical gates**

```bash
./gradlew :feature:catalog:allTests \
  :feature:root:allTests \
  :composeApp:allTests \
  :composeApp:compileAndroidMain \
  :composeApp:linkDebugFrameworkIosSimulatorArm64
```

Expected: all tests and compilation pass. Haze must compile for Android and iOS.

- [ ] **Step 3: Run static and git hygiene checks**

```bash
rg -n 'Share|share|navigationBar|NavigationBar' feature/catalog/src/commonMain
git diff --check
git status --short
```

Expected: no Share or bottom-navigation production UI, clean diff check, only
intended changes before commit.

- [ ] **Step 4: Commit documentation and any final test-only corrections**

```bash
git add AGENTS.md feature/catalog gradle/libs.versions.toml
git commit -m "docs: document native mini-app catalog"
```

- [ ] **Step 5: Request final code review**

Review the full branch diff for state restoration, nested click semantics,
adaptive layout, Haze ownership, platform compilation, and speculative API
growth. Address Critical/Important findings before declaring completion.
