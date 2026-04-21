# R8 Configuration Analysis — BlockBlast

## 1. Build configuration

**Module analysed:** `androidApp/` (the only `com.android.application` module; the rest of the project is library modules consumed via `composeApp`).

### AGP / Gradle

- AGP version: **9.0.0** (`gradle/libs.versions.toml` line 2). No upgrade needed — AGP 9 ships the full set of app-optimization improvements.
- `gradle.properties`: no `android.enableR8.fullMode=false` override — R8 **Full Mode** is on by default for AGP 8.0+.
- `gradle.properties`: no `android.r8.optimizedResourceShrinking=true` needed — AGP 9.0 uses the new resource shrinker unconditionally. Flag only applies to AGP 8.6–8.x.

### Release buildType — before

```kotlin
buildTypes {
    release {
        isMinifyEnabled = false
    }
}
```

R8 was completely **off** for release. No code shrinking, no resource shrinking, no obfuscation — the shipped APK carries every class from the full Compose/Decompose/MVIKotlin/Firebase/AdMob/UMP graph.

### Release buildType — after (applied)

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
    }
}
```

- `isMinifyEnabled = true` → turns on R8 (code shrinking + optimization + obfuscation).
- `isShrinkResources = true` → strips unreferenced resources (icons, drawables, `values-*`).
- `proguard-android-optimize.txt` → AGP's optimized default, required per R8 best practice.
- Project-local `proguard-rules.pro` created (see §3).

## 2. Existing keep rules — libraries check

**Before this change there were no project-level keep rules.** Nothing to flag against `REDUNDANT-RULES.md`, but the principle still applies going forward:

| Library present in the graph | Source of consumer rules |
|---|---|
| AndroidX / `activity-compose` / `lifecycle-*` | AAR consumer rules |
| Compose Multiplatform | AAR consumer rules |
| Kotlin stdlib / Kotlinx coroutines / datetime | JAR consumer rules |
| kotlinx.serialization | Gradle plugin emits keep rules |
| Google Play Services Ads (GMA 25.2) | AAR consumer rules |
| User Messaging Platform (UMP) | AAR consumer rules |
| Firebase BOM / Crashlytics | AAR consumer rules |
| Decompose / Essenty / MVIKotlin | JAR consumer rules |
| multiplatform-settings | JAR consumer rules |
| Metro DI | Compile-time — no runtime reflection, no rules needed |

→ **Do not add keep rules for any of the above.** Each ships its own; duplicating them expands the keep graph and hurts shrinking.

## 3. Project-local keep rules added

Only one category of app-specific reflection exists: **kotlinx.serialization** usage on our own model/nav-config classes.

`@Serializable` is applied to:
- Domain models (`core/domain/.../model/*`) — `GameState`, `Grid`, `Piece`, `Polyomino`, `Position`, `FeedbackType`, `GameEvent`.
- Data-layer persistence (`core/data/.../SettingsBackedGameSaveRepository.kt`).
- Feature navigation `Config` classes (`feature/root/DefaultRootComponent`, `feature/game/DefaultGameComponent`).

The serialization plugin generates a synthetic `Companion.serializer()`, and under R8 full mode the reflective lookup `::class.serializer()` can't be traced back to the Companion. Narrow rules, scoped by package (NOT `-keep class * { *; }`), keep only the synthetic serializer members:

```pro
-keepclassmembers @kotlinx.serialization.Serializable class ge.yet.blokblast.domain.model.** {
    public static ** Companion;
}
-keepclasseswithmembers class ge.yet.blokblast.domain.model.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
```

…and analogous pairs for `ge.yet.blokblast.data.**` and `ge.yet.blockblast.feature.**`. Rationale:

- **Package-scoped, not global.** Skips the library side of serialization, which already has its own rules.
- **Member-scoped, not class-scoped.** `-keep class … { *; }` would retain every field/method of every `@Serializable` type; `keepclassmembers` retains only the Companion reference + `serializer()` method.
- **Annotation-predicated.** `@kotlinx.serialization.Serializable class …` ensures R8 only keeps Companions on types that actually serialize.

No other hand-written keep rules needed:
- **Metro DI** — all bindings resolved at compile time; no `Class.forName` / `::class.java.newInstance`.
- **Decompose** — state-keeper serialization is kotlinx.serialization, already covered.
- **MVIKotlin stores / reducers** — no reflection.
- **Firebase Crashlytics** — stack-trace obfuscation is handled by uploading the mapping file, not by keeping class names. See §5.
- **AdMob / UMP** — Google's AARs ship consumer rules.

## 4. Impact-ordered action summary

1. **Enable R8 release build** — done. Biggest single APK-size win available; keeps nothing unnecessarily because the new rules file is minimal.
2. **kotlinx.serialization keep rules** — done; narrow, annotation-predicated, package-scoped.
3. No redundant library rules were present to remove.
4. No subsuming / overly-broad rules were present to tighten.

## 5. Follow-ups

- **Mapping file upload for Crashlytics.** With `isMinifyEnabled = true` stack traces in Firebase Crashlytics will be obfuscated. The `com.google.firebase.crashlytics` Gradle plugin is already applied in `androidApp/build.gradle.kts`; confirm a release build uploads `mapping.txt` by running `./gradlew :androidApp:assembleRelease` and checking for the `uploadCrashlyticsMappingFile…` task output. No code change needed if the plugin is in place.
- **Run UI Automator smoke test on a release build** touching every reflection-sensitive surface:
  - Cold launch → Home screen (verifies Decompose root `Config` deserialization from saved state).
  - Start game → place piece → game over (verifies `GameState` / `Grid` round-trip through `SettingsBackedGameSaveRepository`).
  - Background → foreground the app while a game is running (verifies `Piece`/`Polyomino`/`Position` state-keeper round-trip).
  - Trigger the game-over interstitial and the bottom banner (verifies AdMob consumer rules still apply under full-mode R8).
  - Toggle all three Settings switches + open Privacy Policy link.
  - Kill process, relaunch — best-score and in-progress game should restore intact.
- **Once shrinking is verified**, inspect APK size delta with `./gradlew :androidApp:assembleRelease` before/after or via the APK Analyzer. Typical win for a Compose MP + Firebase + AdMob app with minify off → on is 30–50% dex size.
- If the release build surfaces any `ClassNotFoundException` / `NoSuchMethodException` from serialization, do NOT widen to `-keep class …`; instead add a narrower `-keepclassmembers @kotlinx.serialization.Serializable class <exact.package>.** { public static ** Companion; }` entry for the offending package.
