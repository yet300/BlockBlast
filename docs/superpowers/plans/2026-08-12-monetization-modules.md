# Monetization Modules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move SDK-free monetization policy and the `basic-ads` AdMob adapter out of `:composeApp`, while preserving ATT/UMP behavior and making policy tests independent from Apple SDK linkage.

**Architecture:** `:monetization:core` owns entitlement and advertising decisions without Compose or native SDKs. `:monetization:basic-ads` owns Compose, `basic-ads`, AdMob, UMP, and the Kotlin ATT bridge; `:composeApp` supplies product configuration and exposes only a minimal Swift facade.

**Tech Stack:** Kotlin 2.4, Kotlin Multiplatform, Compose Multiplatform, basic-ads 1.2.0, GitLive Firebase 3.0.0-alpha01, SwiftPM, Gradle, XCTest/Xcode integration.

---

### Task 1: Commit the GitLive 3.0 upgrade cleanly

**Files:**
- Modify: `.gitignore`
- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/src/commonMain/composeResources/files/aboutlibraries.json`

- [ ] **Step 1: Remove the blanket `.swiftpm-locks` ignore**

Delete the `.swiftpm-locks` line from `.gitignore`. Kotlin SwiftPM import uses the merged `Package.resolved` under this directory for reproducible dependency resolution; checkout and build output remain outside source control.

- [ ] **Step 2: Verify the dependency change is limited to GitLive metadata**

Run:

```bash
git diff --check -- .gitignore gradle/libs.versions.toml composeApp/src/commonMain/composeResources/files/aboutlibraries.json
./gradlew :core:telemetry:compileKotlinIosSimulatorArm64
```

Expected: diff check passes and telemetry compiles with `dev.gitlive` version `3.0.0-alpha01`.

- [ ] **Step 3: Commit only the GitLive upgrade**

```bash
git add gradle/libs.versions.toml composeApp/src/commonMain/composeResources/files/aboutlibraries.json .gitignore
git commit -m "build: update GitLive Firebase to 3.0 alpha"
```

Expected: unrelated generated SwiftPM files are not part of this commit.

### Task 2: Add the SDK-free monetization policy module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `monetization/core/build.gradle.kts`
- Create: `monetization/core/src/commonMain/kotlin/ge/yet/blockblast/monetization/core/MonetizationState.kt`
- Create: `monetization/core/src/commonMain/kotlin/ge/yet/blockblast/monetization/core/AdsPolicy.kt`
- Create: `monetization/core/src/commonTest/kotlin/ge/yet/blockblast/monetization/core/AdsPolicyTest.kt`
- Delete: `composeApp/src/commonTest/kotlin/ge/yet3/blokblast/ads/AdsPolicyTest.kt`

- [ ] **Step 1: Register the modules and create the core build**

Add to `settings.gradle.kts`:

```kotlin
include(":monetization")
include(":monetization:core")
include(":monetization:basic-ads")
```

Create `monetization/core/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
}
```

- [ ] **Step 2: Write the policy tests before moving implementation**

Create tests covering every truth-table combination for preference, consent,
and entitlement, plus interstitial readiness and exact-once completion:

```kotlin
class AdsPolicyTest {
    @Test
    fun ads_require_preference_consent_and_free_entitlement() {
        assertTrue(MonetizationState(true, true, MonetizationEntitlement.FREE).canShowAds)
        assertFalse(MonetizationState(false, true, MonetizationEntitlement.FREE).canShowAds)
        assertFalse(MonetizationState(true, false, MonetizationEntitlement.FREE).canShowAds)
        assertFalse(MonetizationState(true, true, MonetizationEntitlement.AD_FREE).canShowAds)
    }

    @Test
    fun interstitial_requires_permission_and_readiness() {
        assertTrue(shouldShowInterstitial(adsAllowed = true, isReady = true))
        assertFalse(shouldShowInterstitial(adsAllowed = false, isReady = true))
        assertFalse(shouldShowInterstitial(adsAllowed = true, isReady = false))
    }

    @Test
    fun completion_runs_once() {
        var calls = 0
        val complete = once { calls++ }
        complete()
        complete()
        assertEquals(1, calls)
    }
}
```

- [ ] **Step 3: Run the new tests and verify they fail**

Run:

```bash
./gradlew :monetization:core:allTests
```

Expected: compilation fails because the policy types do not exist.

- [ ] **Step 4: Implement the minimal SDK-free policy**

`MonetizationState.kt`:

```kotlin
enum class MonetizationEntitlement { FREE, AD_FREE }

data class MonetizationState(
    val adsPreferenceEnabled: Boolean,
    val consentAllowsAds: Boolean,
    val entitlement: MonetizationEntitlement,
) {
    val canShowAds: Boolean
        get() = adsPreferenceEnabled &&
            consentAllowsAds &&
            entitlement != MonetizationEntitlement.AD_FREE
}
```

`AdsPolicy.kt`:

```kotlin
fun shouldShowInterstitial(adsAllowed: Boolean, isReady: Boolean): Boolean =
    adsAllowed && isReady

fun once(action: () -> Unit): () -> Unit {
    var invoked = false
    return {
        if (!invoked) {
            invoked = true
            action()
        }
    }
}
```

- [ ] **Step 5: Run policy tests**

```bash
./gradlew :monetization:core:allTests
```

Expected: all tests pass without resolving Firebase, GoogleMobileAds, or UMP.

- [ ] **Step 6: Commit the policy module**

```bash
git add settings.gradle.kts monetization/core composeApp/src/commonTest/kotlin/ge/yet3/blokblast/ads/AdsPolicyTest.kt
git commit -m "feat: add SDK-free monetization policy"
```

### Task 3: Create the basic-ads adapter module

**Files:**
- Create: `monetization/basic-ads/build.gradle.kts`
- Create: `monetization/basic-ads/src/commonMain/kotlin/ge/yet/blockblast/monetization/ads/AdMobConfiguration.kt`
- Create: `monetization/basic-ads/src/commonMain/kotlin/ge/yet/blockblast/monetization/ads/AdMobProvider.kt`
- Move: `composeApp/src/commonMain/kotlin/ge/yet3/blokblast/ads/AdsConsent.kt`
- Move: `composeApp/src/androidMain/kotlin/ge/yet3/blokblast/ads/AdsConsent.android.kt`
- Move: `composeApp/src/iosMain/kotlin/ge/yet3/blokblast/ads/AdsConsent.ios.kt`
- Move: `composeApp/src/commonMain/kotlin/ge/yet3/blokblast/ads/AdBanner.kt`
- Move: `composeApp/src/commonMain/kotlin/ge/yet3/blokblast/ads/Interstitial.kt`
- Delete: `composeApp/src/commonMain/kotlin/ge/yet3/blokblast/ads/AdsPolicy.kt`

- [ ] **Step 1: Configure Compose, Android SDKs, and SwiftPM dependencies**

Create a KMP Compose module depending on `projects.monetization.core` and
`libs.basic.ads`. Move `libs.play.services.ads` and
`libs.user.messaging.platform` from `:composeApp` to its `androidMain`.

The build file starts with:

```kotlin
plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    swiftPMDependencies {
        // declarations below
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.monetization.core)
            implementation(libs.basic.ads)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
        androidMain.dependencies {
            implementation(libs.play.services.ads)
            implementation(libs.user.messaging.platform)
        }
    }
}
```

Declare Swift packages in `kotlin { swiftPMDependencies { ... } }`:

```kotlin
iosMinimumDeploymentTarget.set("15.0")
swiftPackage(
    url = url("https://github.com/googleads/swift-package-manager-google-mobile-ads.git"),
    version = exact("13.3.0"),
    products = listOf(product("GoogleMobileAds")),
)
swiftPackage(
    url = url("https://github.com/googleads/swift-package-manager-google-user-messaging-platform.git"),
    version = exact("3.1.0"),
    products = listOf(product("GoogleUserMessagingPlatform")),
)
```

- [ ] **Step 2: Add product configuration and provider state**

Create:

```kotlin
data class AdMobConfiguration(
    val bannerAndroidUnitId: String,
    val bannerIosUnitId: String,
    val gameOverInterstitialAndroidUnitId: String,
    val gameOverInterstitialIosUnitId: String,
)
```

`rememberAdMobState` must request consent only when preference is enabled and
the entitlement is not `AD_FREE`. `AdMobProvider` must initialize `BasicAds`
only when `state.canShowAds`, then provide state and configuration through
adapter-owned CompositionLocals.

```kotlin
val LocalMonetizationState = staticCompositionLocalOf {
    MonetizationState(
        adsPreferenceEnabled = false,
        consentAllowsAds = false,
        entitlement = MonetizationEntitlement.FREE,
    )
}

internal val LocalAdMobConfiguration =
    staticCompositionLocalOf<AdMobConfiguration?> { null }

@Composable
fun rememberAdMobState(
    preferenceEnabled: Boolean,
    entitlement: MonetizationEntitlement,
): MonetizationState {
    val adsRequested =
        preferenceEnabled && entitlement != MonetizationEntitlement.AD_FREE
    val consentAllowsAds = rememberAdsConsentAllowsRequests(adsRequested)
    return MonetizationState(
        adsPreferenceEnabled = preferenceEnabled,
        consentAllowsAds = consentAllowsAds,
        entitlement = entitlement,
    )
}

@Composable
fun AdMobProvider(
    state: MonetizationState,
    configuration: AdMobConfiguration,
    content: @Composable () -> Unit,
) {
    if (state.canShowAds) BasicAds.Initialize()
    CompositionLocalProvider(
        LocalMonetizationState provides state,
        LocalAdMobConfiguration provides configuration,
        content = content,
    )
}
```

- [ ] **Step 3: Move consent and ATT bridge implementations**

Rename packages to `ge.yet.blockblast.monetization.ads`. Preserve these
semantics:

- Android has no ATT prerequisite.
- iOS invokes ATT once and waits for `markCompleted` for every ATT status.
- UMP error paths publish cached `Consent.canRequestAds`.

Expose the adapter bridge as `AdMobTrackingAuthorizationBridge`; do not expose
the entire adapter module through the final iOS framework.

- [ ] **Step 4: Move banner and interstitial adapters**

Read IDs from `LocalAdMobConfiguration`, permission from
`LocalMonetizationState.current.canShowAds`, and map readiness with:

```kotlin
shouldShowInterstitial(
    adsAllowed = state.canShowAds,
    isReady = interstitialAd.state == AdState.READY,
)
```

Retain exact-once completion and reload behavior.

- [ ] **Step 5: Compile the adapter on Android and iOS**

```bash
./gradlew :monetization:basic-ads:compileAndroidMain \
  :monetization:basic-ads:compileKotlinIosSimulatorArm64
```

Expected: both targets compile and the SwiftPM metadata tasks resolve Ads/UMP.

- [ ] **Step 6: Commit the adapter module**

```bash
git add monetization/basic-ads composeApp/src/commonMain/kotlin/ge/yet3/blokblast/ads composeApp/src/androidMain/kotlin/ge/yet3/blokblast/ads composeApp/src/iosMain/kotlin/ge/yet3/blokblast/ads
git commit -m "feat: isolate basic-ads adapter"
```

### Task 4: Integrate monetization into the application shell

**Files:**
- Modify: `composeApp/build.gradle.kts`
- Modify: `composeApp/src/commonMain/kotlin/ge/yet3/blokblast/screen/App.kt`
- Modify: `composeApp/src/commonMain/kotlin/ge/yet3/blokblast/component/utils/LocalSettings.kt`
- Modify: `composeApp/src/commonMain/kotlin/ge/yet3/blokblast/screen/result/GameResultContent.kt`
- Modify imports in: `composeApp/src/commonMain/kotlin/ge/yet3/blokblast/screen/game/BlockBlastGameContent.kt`
- Modify imports in: `composeApp/src/commonMain/kotlin/ge/yet3/blokblast/screen/home/HomeContent.kt`
- Create: `composeApp/src/iosMain/kotlin/ge/yet3/blokblast/ads/IosTrackingAuthorizationBridge.kt`

- [ ] **Step 1: Replace direct SDK dependencies**

Remove `libs.basic.ads`, `libs.play.services.ads`, and
`libs.user.messaging.platform` from `:composeApp`. Add:

```kotlin
implementation(projects.monetization.basicAds)
```

- [ ] **Step 2: Install the provider in `App`**

Construct `AdMobConfiguration` from `AppConfig`, use entitlement `FREE`, call
`rememberAdMobState`, and wrap `RootContent` in `AdMobProvider`. Remove direct
`BasicAds`, consent, and `LocalAdsEnabled` usage.

- [ ] **Step 3: Update consumers**

Import `AdBanner`, `rememberGameOverInterstitial`, and
`LocalMonetizationState` from the adapter. Delete `LocalAdsEnabled` from
`LocalSettings.kt`. Result layout receives
`LocalMonetizationState.current.canShowAds`.

- [ ] **Step 4: Preserve the Swift API with a narrow facade**

Keep Swift source unchanged by exposing `IosTrackingAuthorizationBridge` from
`:composeApp` and delegating `requestAuthorization` and `markCompleted()` to
`AdMobTrackingAuthorizationBridge`.

```kotlin
object IosTrackingAuthorizationBridge {
    var requestAuthorization: (() -> Unit)?
        get() = AdMobTrackingAuthorizationBridge.requestAuthorization
        set(value) {
            AdMobTrackingAuthorizationBridge.requestAuthorization = value
        }

    fun markCompleted() {
        AdMobTrackingAuthorizationBridge.markCompleted()
    }
}
```

- [ ] **Step 5: Compile application targets**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid \
  :composeApp:linkDebugFrameworkIosSimulatorArm64
```

Expected: no `app.lexilabs.basic.ads` import remains under `composeApp/src` and
both targets compile.

- [ ] **Step 6: Commit application integration**

```bash
git add composeApp
git commit -m "refactor: consume monetization adapter"
```

### Task 5: Integrate the synthetic SwiftPM linkage package

**Files:**
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj`
- Modify: `.swiftpm-locks/default/swiftImport/Package.resolved`
- Modify: `.gitignore` only if Kotlin tooling adds an incorrect blanket rule

- [ ] **Step 1: Generate and integrate linkage metadata**

```bash
./gradlew :composeApp:fetchSyntheticImportProjectPackages
XCODEPROJ_PATH="$PWD/iosApp/iosApp.xcodeproj" \
  ./gradlew :composeApp:integrateLinkagePackage -i
```

Expected: the Xcode project references the generated Kotlin linkage package and
the merged lock contains Firebase, GoogleMobileAds, and UMP pins.

- [ ] **Step 2: Remove duplicate direct Xcode package products**

Remove the direct application-target product dependencies for FirebaseCore,
FirebaseAnalytics, FirebaseCrashlytics, GoogleMobileAds, and
GoogleUserMessagingPlatform only after the generated linkage product is
present. Keep the Crashlytics upload script.

- [ ] **Step 3: Verify the complete Xcode build**

```bash
xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

Expected: `BUILD SUCCEEDED` with no missing or duplicate framework symbols.

- [ ] **Step 4: Commit Xcode integration and locks**

```bash
git add iosApp/iosApp.xcodeproj/project.pbxproj .swiftpm-locks .gitignore
git commit -m "build: integrate native monetization packages"
```

### Task 6: Update architecture documentation and run final verification

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1: Document new modules and dependency direction**

Add `monetization/core` and `monetization/basic-ads` to the repository map,
module table, dependency rules, and verification commands.

- [ ] **Step 2: Run focused and broad verification**

```bash
./gradlew :monetization:core:allTests
./gradlew :monetization:basic-ads:compileKotlinIosSimulatorArm64
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

Then repeat the full Xcode simulator build. Expected: every command succeeds.

- [ ] **Step 3: Confirm dependency isolation**

```bash
rg -n 'app\.lexilabs\.basic\.ads' composeApp/src
./gradlew :monetization:core:dependencies
```

Expected: no imports in `composeApp/src`; core dependency output contains no
Compose, Firebase, Google Mobile Ads, UMP, or `basic-ads`.

- [ ] **Step 4: Commit documentation**

```bash
git add AGENTS.md
git commit -m "docs: document monetization modules"
```
