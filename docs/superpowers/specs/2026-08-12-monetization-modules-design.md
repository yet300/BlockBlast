# Monetization Modules Design

## Context

Advertising is currently implemented directly in `:composeApp`. The code mixes
four responsibilities: monetization policy, user consent, Google Mobile Ads SDK
integration, and Compose presentation. This makes pure policy tests inherit the
native Firebase, Google Mobile Ads, and User Messaging Platform linker
requirements of the aggregate application module.

The application currently uses AdMob through `basic-ads`. It is expected to add
an ad-free subscription later. The design therefore needs a monetization
boundary without introducing a subscription SDK before one is selected.

## Goals

- Isolate pure monetization decisions from Compose and native SDKs.
- Encapsulate all `basic-ads`, AdMob, and UMP usage in one adapter module.
- Preserve the existing ATT-before-UMP consent sequence on iOS.
- Prepare the policy model for a future ad-free subscription entitlement.
- Allow monetization policy tests to run without linking Apple SDK frameworks.
- Keep feature and core dependency directions consistent with `AGENTS.md`.

## Non-goals

- Selecting or integrating a subscription provider.
- Adding purchases, paywalls, receipt validation, or restore-purchase flows.
- Replacing AdMob or supporting multiple ad networks.
- Making standalone Kotlin/Native tests of native SDK adapters a required CI
  gate.

## Module Structure

```text
:composeApp
    |
    v
:monetization:basic-ads
    |
    v
:monetization:core

Future:
:monetization:subscriptions -> :monetization:core
```

### `:monetization:core`

This is a Kotlin Multiplatform library with no Compose, `basic-ads`, Firebase,
Google Mobile Ads, UMP, or Apple framework dependencies.

It owns:

- `MonetizationEntitlement` with `FREE` and `AD_FREE` states.
- `MonetizationState` and the derived `canShowAds` decision.
- SDK-neutral advertising policy functions.
- An internal SDK-free exact-once completion guard used by interstitial policy.
- Unit tests for entitlement, consent, preference, readiness, and exact-once
  completion behavior.

The initial state shape is:

```kotlin
enum class MonetizationEntitlement {
    FREE,
    AD_FREE,
}

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

Interstitial readiness must not expose `basic-ads` types to this module. The
policy accepts an SDK-neutral Boolean:

```kotlin
fun shouldShowInterstitial(
    adsAllowed: Boolean,
    isReady: Boolean,
): Boolean
```

### `:monetization:basic-ads`

This is a Compose Multiplatform adapter depending on
`:monetization:core` and `app.lexilabs.basic:basic-ads`.

It owns:

- AdMob and UMP SDK initialization.
- Android and iOS consent implementations.
- The Kotlin side of the iOS ATT bridge.
- Banner and game-over interstitial Compose integration.
- Mapping `basic-ads` states such as `AdState.READY` into SDK-neutral policy
  inputs.
- Google Mobile Ads and UMP SwiftPM dependency declarations.
- The monetization CompositionLocal/provider used by application UI.

The public Compose-facing API will be kept small:

- `rememberAdMobState(preferenceEnabled, entitlement)` obtains consent and
  returns `MonetizationState`.
- `AdMobProvider(state, content)` initializes the SDK only when permitted and
  provides the state to descendants.
- `AdBanner` renders the configured banner when ads are permitted.
- `rememberGameOverInterstitial` returns a completion-safe show callback.

Ad unit IDs are supplied from `:composeApp` through an `AdMobConfiguration`
value passed to `AdMobProvider`; the module must not own product-specific IDs.

### `:composeApp`

The application composition root will:

- Read the user's ads preference from `RootComponent`.
- Supply `FREE` until a subscription implementation exists.
- Create the current monetization state through the adapter.
- Install `AdMobProvider` around application content.
- Consume adapter UI functions without importing `basic-ads` directly.

The direct `composeApp -> basic-ads` dependency and direct SDK imports will be
removed.

### `iosApp`

`TrackingAuthorizationManager.swift` remains in the native host because it owns
the system ATT prompt and application lifecycle presentation. It calls the
exported Kotlin ATT bridge, which moves to `:monetization:basic-ads`.

The `ComposeApp` framework will expose a minimal iOS-only facade that delegates
to the adapter's ATT bridge. The adapter module itself will not be exported to
Swift, keeping Compose and SDK implementation details out of the public
Objective-C/Swift header.

## State and Consent Flow

```text
ads preference enabled
        |
        v
iOS ATT completed (any result) / Android immediately ready
        |
        v
UMP refresh and optional form
        |
        v
UMP canRequestAds
        |
        +---- ad-free entitlement
        |
        v
MonetizationState.canShowAds
        |
        +---- BasicAds initialization
        +---- banner visibility
        +---- interstitial loading and display
```

ATT denial completes the prerequisite but does not itself prohibit contextual
ads. UMP remains authoritative for whether ads can be requested. No ad request
or SDK initialization occurs before `canShowAds` becomes true.

The current application supplies `FREE` because no purchase source exists yet.
When subscriptions are implemented, their loading state must keep ads disabled
until the entitlement resolves, so a paid user never briefly sees an ad. That
loading state belongs to the future subscription integration and is outside
this migration.

## Error Handling

- An ATT callback completes the bridge for every authorization status.
- A UMP refresh or form error falls back to UMP's cached `canRequestAds` value.
- An unavailable interstitial continues the requested navigation immediately.
- An interstitial load, presentation, or dismissal failure invokes navigation
  completion exactly once and schedules/retries loading according to the
  existing adapter behavior.
- SDK exceptions do not escape into gameplay components.
- Ads remain disabled when there is no authoritative consent result.

## SwiftPM and Xcode Integration

The adapter declares the native versions required by `basic-ads 1.2.0`:

- Google Mobile Ads `13.3.0`.
- Google User Messaging Platform `3.1.0`.

Firebase continues to arrive transitively from GitLive 3.0 metadata. The
aggregate framework remains static. After the module migration, the generated
linkage package is connected with:

```bash
XCODEPROJ_PATH="$PWD/iosApp/iosApp.xcodeproj" \
./gradlew :composeApp:integrateLinkagePackage -i
```

Once the generated package is verified, duplicate direct Firebase, Google
Mobile Ads, and UMP product dependencies are removed from the Xcode application
target. The Crashlytics symbol upload build phase remains.

Generated `.swiftpm-locks` contents and the relevant Xcode project changes are
committed for reproducible dependency resolution.

## Testing Strategy

### Pure tests

Move `AdsPolicyTest` to `:monetization:core:commonTest` and expand it to cover:

- disabled user preference;
- missing or denied consent;
- free entitlement;
- ad-free entitlement;
- ready and unavailable interstitial states;
- exact-once completion after failure and dismissal races.

Run the module's multiplatform tests independently. These tests must not resolve
Firebase or advertising frameworks.

### Adapter verification

Verify `:monetization:basic-ads` through:

- common and Android compilation;
- Android host tests for SDK-neutral adapter behavior where practical;
- iOS framework linking;
- the Xcode-integrated simulator build.

Standalone Apple test executables that include native advertising SDKs are not
a required gate because Kotlin/Native may link them outside Xcode's package
integration context.

### Application verification

Verify that:

- Android application assembly succeeds;
- `ComposeApp` links as a static iOS simulator framework;
- the complete Xcode simulator application builds;
- no ad request occurs before ATT/UMP completion;
- an unavailable interstitial never blocks result-screen navigation.

## Migration Sequence

1. Add both modules and their Gradle configuration.
2. Implement and test the SDK-free policy model in `:monetization:core`.
3. Move consent, ATT bridge, banner, and interstitial code into the adapter.
4. Replace direct `basic-ads` use in `:composeApp` with the adapter API.
5. Add transitive SwiftPM declarations for Google Mobile Ads and UMP.
6. Integrate the generated linkage package into the Xcode project.
7. Remove verified duplicate direct Xcode package products.
8. Run Android, Kotlin/Native framework, policy, and full Xcode verification.
9. Update `AGENTS.md` to document the new modules and dependency direction.

## Acceptance Criteria

- `:monetization:core` contains no Compose or native SDK dependency.
- No source in `:composeApp` imports `app.lexilabs.basic.ads`.
- All AdMob and UMP Kotlin integration is contained in
  `:monetization:basic-ads`.
- Monetization policy tests pass without linking Firebase or Google frameworks.
- ATT precedes UMP on iOS and all ATT statuses complete the flow.
- Paid/ad-free entitlement prevents SDK initialization and ad requests.
- Android assembly, iOS framework linking, and the Xcode simulator build pass.
- SwiftPM packages are resolved once through the generated linkage integration,
  without duplicate product linkage.
