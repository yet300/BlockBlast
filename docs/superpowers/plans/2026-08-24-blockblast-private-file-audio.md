# Block Blast Private File Audio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore Block Blast's bundled MP3 soundtrack and voice feedback as a private session-scoped implementation while removing the obsolete shared file-audio API from `core` and `feature:root`.

**Architecture:** `BlockBlastAudioPlayer` remains the semantic interface consumed by game code. A common session policy gates a private platform player with `FeedbackPreferences`, MiniApp visibility, and Decompose lifecycle; Android uses `SoundPool`/`MediaPlayer`, while iOS uses `AVAudioPlayer` and the existing Block Blast Compose resource reader. Generic `MiniAppAudio` remains procedural-only and unchanged.

**Tech Stack:** Kotlin Multiplatform, Metro DI, Decompose/Essenty lifecycle, Kotlin Coroutines/StateFlow, Compose Multiplatform Resources, Android `SoundPool`/`MediaPlayer`, iOS `AVAudioPlayer`.

---

### Task 1: Expose music through the narrow feedback preference projection

**Files:**
- Modify: `core/domain/src/commonMain/kotlin/ge/yet/game/domain/repository/FeedbackPreferences.kt`
- Modify: `core/domain/src/commonMain/kotlin/ge/yet/game/domain/repository/SettingsRepository.kt`
- Modify: `core/data/src/commonTest/kotlin/ge/yet/game/data/repository/SettingsBackedSettingsRepositoryTest.kt`
- Modify: `game/blockblast/src/commonTest/kotlin/ge/yet/game/blockblast/di/BlockBlastSessionGraphTest.kt`

- [ ] **Step 1: Extend the projection test to include music identity**

Replace the two-pair projection with explicit triples so the test proves all
three feedback flows come from the same Settings-backed instance:

```kotlin
internal data class FeedbackFlowProjection(
    val settingsMusic: StateFlow<Boolean>,
    val settingsSfx: StateFlow<Boolean>,
    val settingsVibration: StateFlow<Boolean>,
    val feedbackMusic: StateFlow<Boolean>,
    val feedbackSfx: StateFlow<Boolean>,
    val feedbackVibration: StateFlow<Boolean>,
)

@Provides
internal fun provideFeedbackFlowProjection(
    settingsRepository: SettingsRepository,
    feedbackPreferences: FeedbackPreferences,
): FeedbackFlowProjection = FeedbackFlowProjection(
    settingsMusic = settingsRepository.musicEnabled,
    settingsSfx = settingsRepository.sfxEnabled,
    settingsVibration = settingsRepository.vibrationEnabled,
    feedbackMusic = feedbackPreferences.musicEnabled,
    feedbackSfx = feedbackPreferences.sfxEnabled,
    feedbackVibration = feedbackPreferences.vibrationEnabled,
)
```

Update the identity assertions:

```kotlin
assertSame(projection.settingsMusic, projection.feedbackMusic)
assertSame(projection.settingsSfx, projection.feedbackSfx)
assertSame(projection.settingsVibration, projection.feedbackVibration)
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew :core:data:allTests
```

Expected: compilation fails because `FeedbackPreferences.musicEnabled` does not
exist.

- [ ] **Step 3: Add music to `FeedbackPreferences`**

```kotlin
interface FeedbackPreferences {
    val musicEnabled: StateFlow<Boolean>
    val sfxEnabled: StateFlow<Boolean>
    val vibrationEnabled: StateFlow<Boolean>
}
```

Mark the existing `SettingsRepository.musicEnabled` declaration as an override:

```kotlin
override val musicEnabled: StateFlow<Boolean>
```

Add this flow to `TestFeedbackPreferences` in the Block Blast graph test:

```kotlin
private val music = MutableStateFlow(true)
override val musicEnabled: StateFlow<Boolean> = music.asStateFlow()
```

- [ ] **Step 4: Run domain, data, and Block Blast tests**

Run:

```bash
./gradlew :core:domain:allTests :core:data:allTests :game:blockblast:allTests
```

Expected: all tasks pass.

- [ ] **Step 5: Commit the preference projection**

```bash
git add core/domain/src/commonMain/kotlin/ge/yet/game/domain/repository/FeedbackPreferences.kt \
  core/domain/src/commonMain/kotlin/ge/yet/game/domain/repository/SettingsRepository.kt \
  core/data/src/commonTest/kotlin/ge/yet/game/data/repository/SettingsBackedSettingsRepositoryTest.kt \
  game/blockblast/src/commonTest/kotlin/ge/yet/game/blockblast/di/BlockBlastSessionGraphTest.kt
git commit -m "refactor: expose music through feedback preferences"
```

### Task 2: Add typed file assets beside the still-wired procedural declaration

**Files:**
- Create: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/data/audio/BlockBlastAudioAssets.kt`
- Create: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/data/audio/BlockBlastPlatformAudioPlayer.kt`
- Modify: `game/blockblast/src/commonTest/kotlin/ge/yet/game/blockblast/data/audio/BlockBlastAudioTest.kt`

- [ ] **Step 1: Add asset mapping tests beside the procedural regression tests**

Use exact filenames and keep the non-repeating selector test portable:

```kotlin
@Test
fun playlist_contains_the_three_existing_tracks() {
    assertEquals(
        listOf("block.mp3", "feltwood.mp3", "mossy.mp3"),
        BlockBlastAudioAssets.music,
    )
}

@Test
fun every_feedback_type_maps_to_its_existing_voice_asset() {
    assertEquals("voice_good.mp3", BlockBlastAudioAssets.voice(FeedbackType.GOOD))
    assertEquals("voice_great.mp3", BlockBlastAudioAssets.voice(FeedbackType.GREAT))
    assertEquals("voice_amazing.mp3", BlockBlastAudioAssets.voice(FeedbackType.AMAZING))
    assertEquals("voice_excellent.mp3", BlockBlastAudioAssets.voice(FeedbackType.EXCELLENT))
    assertEquals("voice_unbelievable.mp3", BlockBlastAudioAssets.voice(FeedbackType.UNBELIEVABLE))
}

@Test
fun next_track_does_not_repeat_when_playlist_has_multiple_tracks() {
    val next = nextTrackIndex(trackCount = 3, previous = 1, random = Random(7))
    assertNotEquals(1, next)
    assertTrue(next in 0..2)
}
```

Keep the existing procedural assertions and `RecordingMiniAppAudio` fixture
temporarily. They protect the currently wired path until Task 5 switches the
session graph atomically.

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
./gradlew :game:blockblast:allTests
```

Expected: compilation fails because `BlockBlastAudioAssets` and the game-local
`nextTrackIndex` do not exist.

- [ ] **Step 3: Introduce the typed asset catalog and private platform boundary**

Create `BlockBlastAudioAssets.kt` without changing the still-wired procedural
`BlockBlastAudio.kt`:

```kotlin
package ge.yet.game.blockblast.data.audio

import ge.yet.game.blockblast.domain.model.FeedbackType

internal object BlockBlastAudioAssets {
    val music: List<String> = listOf("block.mp3", "feltwood.mp3", "mossy.mp3")

    fun voice(type: FeedbackType): String = when (type) {
        FeedbackType.GOOD -> "voice_good.mp3"
        FeedbackType.GREAT -> "voice_great.mp3"
        FeedbackType.AMAZING -> "voice_amazing.mp3"
        FeedbackType.EXCELLENT -> "voice_excellent.mp3"
        FeedbackType.UNBELIEVABLE -> "voice_unbelievable.mp3"
    }
}

```

Create `BlockBlastPlatformAudioPlayer.kt`:

```kotlin
package ge.yet.game.blockblast.data.audio

import kotlin.random.Random

internal interface BlockBlastPlatformAudioPlayer {
    fun playVoice(filename: String)
    fun startMusic(tracks: List<String>)
    fun stopMusic()
    fun release()
}

internal fun nextTrackIndex(
    trackCount: Int,
    previous: Int,
    random: Random = Random,
): Int {
    if (trackCount <= 1) return 0
    var next = random.nextInt(trackCount)
    while (next == previous) next = random.nextInt(trackCount)
    return next
}
```

- [ ] **Step 4: Run the focused test**

Run:

```bash
./gradlew :game:blockblast:allTests
```

Expected: all focused tests pass.

- [ ] **Step 5: Commit typed Block Blast audio assets**

```bash
git add game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/data/audio/BlockBlastAudioAssets.kt \
  game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/data/audio/BlockBlastPlatformAudioPlayer.kt \
  game/blockblast/src/commonTest/kotlin/ge/yet/game/blockblast/data/audio/BlockBlastAudioTest.kt
git commit -m "refactor: model Block Blast bundled audio assets"
```

### Task 3: Implement session-owned audio policy

**Files:**
- Create: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/data/audio/DefaultBlockBlastFileAudioPlayer.kt`
- Modify: `game/blockblast/src/commonTest/kotlin/ge/yet/game/blockblast/data/audio/BlockBlastAudioTest.kt`

- [ ] **Step 1: Add policy tests with recording platform and mutable inputs**

Build the fixture with the reusable lifecycle/visibility testkit, mutable
preference flows, and a recording `BlockBlastPlatformAudioPlayer`. Add these
imports and fixtures beside the tests:

```kotlin
private val dispatcher = StandardTestDispatcher()

@BeforeTest
fun setUpMainDispatcher() {
    Dispatchers.setMain(dispatcher)
}

@AfterTest
fun resetMainDispatcher() {
    Dispatchers.resetMain()
}

private fun setup(): AudioSetup {
    val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
    val visibility = MutableMiniAppVisibilitySource()
    val preferences = MutableFeedbackPreferences()
    val platform = RecordingPlatformAudioPlayer()
    return AudioSetup(
        lifecycle = lifecycle,
        visibility = visibility,
        preferences = preferences,
        platform = platform,
        player = DefaultBlockBlastFileAudioPlayer(
            platform = platform,
            preferences = preferences,
            visibility = visibility,
            componentContext = lifecycle.componentContext,
        ),
    )
}

private data class AudioSetup(
    val lifecycle: MiniAppLifecycleHarness,
    val visibility: MutableMiniAppVisibilitySource,
    val preferences: MutableFeedbackPreferences,
    val platform: RecordingPlatformAudioPlayer,
    val player: DefaultBlockBlastFileAudioPlayer,
) {
    fun destroy() = lifecycle.destroy()
}

private class MutableFeedbackPreferences : FeedbackPreferences {
    val music = MutableStateFlow(true)
    val sfx = MutableStateFlow(true)
    private val vibration = MutableStateFlow(true)
    override val musicEnabled: StateFlow<Boolean> = music.asStateFlow()
    override val sfxEnabled: StateFlow<Boolean> = sfx.asStateFlow()
    override val vibrationEnabled: StateFlow<Boolean> = vibration.asStateFlow()
}

private class RecordingPlatformAudioPlayer : BlockBlastPlatformAudioPlayer {
    val voices = mutableListOf<String>()
    val starts = mutableListOf<List<String>>()
    var stopCount = 0
    var releaseCount = 0

    override fun playVoice(filename: String) {
        voices += filename
    }

    override fun startMusic(tracks: List<String>) {
        starts += tracks.toList()
    }

    override fun stopMusic() {
        stopCount += 1
    }

    override fun release() {
        releaseCount += 1
    }
}
```

Add these behaviors:

```kotlin
@Test
fun feedback_routes_the_exact_voice_asset_only_while_active_and_enabled() = runTest(dispatcher) {
    val setup = setup()
    setup.player.playFeedback(FeedbackType.EXCELLENT)
    assertEquals(listOf("voice_excellent.mp3"), setup.platform.voices)

    setup.preferences.sfx.value = false
    setup.player.playFeedback(FeedbackType.GOOD)
    setup.preferences.sfx.value = true
    setup.visibility.set(MiniAppVisibility.OBSCURED)
    setup.player.playFeedback(FeedbackType.GREAT)

    assertEquals(listOf("voice_excellent.mp3"), setup.platform.voices)
    setup.destroy()
}

@Test
fun requested_music_tracks_visibility_and_music_preference() = runTest(dispatcher) {
    val setup = setup()
    setup.player.startMusic()
    runCurrent()
    assertEquals(listOf(BlockBlastAudioAssets.music), setup.platform.starts)

    setup.visibility.set(MiniAppVisibility.OBSCURED)
    runCurrent()
    assertEquals(1, setup.platform.stopCount)

    setup.visibility.set(MiniAppVisibility.ACTIVE)
    runCurrent()
    assertEquals(2, setup.platform.starts.size)

    setup.preferences.music.value = false
    runCurrent()
    assertEquals(2, setup.platform.stopCount)
    setup.destroy()
}

@Test
fun explicit_stop_prevents_visibility_from_restarting_music() = runTest(dispatcher) {
    val setup = setup()
    setup.player.startMusic()
    runCurrent()
    setup.player.stopMusic()
    runCurrent()
    val starts = setup.platform.starts.size

    setup.visibility.set(MiniAppVisibility.OBSCURED)
    setup.visibility.set(MiniAppVisibility.ACTIVE)
    runCurrent()

    assertEquals(starts, setup.platform.starts.size)
    setup.destroy()
}

@Test
fun destroy_releases_once_and_ignores_late_commands() = runTest(dispatcher) {
    val setup = setup()
    setup.destroy()
    setup.destroy()
    setup.player.startMusic()
    setup.player.playFeedback(FeedbackType.AMAZING)
    runCurrent()

    assertEquals(1, setup.platform.releaseCount)
    assertTrue(setup.platform.starts.isEmpty())
    assertTrue(setup.platform.voices.isEmpty())
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew :game:blockblast:allTests
```

Expected: compilation fails because the new session policy controller does not
exist.

- [ ] **Step 3: Implement the not-yet-wired `DefaultBlockBlastFileAudioPlayer`**

Use a lifecycle-owned scope and one serialized music collector:

```kotlin
internal class DefaultBlockBlastFileAudioPlayer(
    private val platform: BlockBlastPlatformAudioPlayer,
    private val preferences: FeedbackPreferences,
    private val visibility: MiniAppVisibilitySource,
    componentContext: ComponentContext,
) : BlockBlastAudioPlayer {
    private val scope = componentContext.coroutineScope()
    private val requestedMusic = MutableStateFlow(false)
    private var destroyed = false

    init {
        scope.launch {
            combine(
                requestedMusic,
                preferences.musicEnabled,
                visibility.visibility,
            ) { requested, enabled, sessionVisibility ->
                requested && enabled && sessionVisibility == MiniAppVisibility.ACTIVE
            }
                .distinctUntilChanged()
                .collect { shouldPlay ->
                    if (shouldPlay) {
                        platform.startMusic(BlockBlastAudioAssets.music)
                    } else {
                        platform.stopMusic()
                    }
                }
        }
        componentContext.lifecycle.doOnDestroy {
            if (!destroyed) {
                destroyed = true
                requestedMusic.value = false
                platform.release()
            }
        }
    }

    override fun playFeedback(type: FeedbackType) {
        if (
            !destroyed &&
            preferences.sfxEnabled.value &&
            visibility.visibility.value == MiniAppVisibility.ACTIVE
        ) {
            platform.playVoice(BlockBlastAudioAssets.voice(type))
        }
    }

    override fun startMusic() {
        if (!destroyed) requestedMusic.value = true
    }

    override fun stopMusic() {
        requestedMusic.value = false
    }
}
```

- [ ] **Step 4: Run the focused and complete Block Blast common tests**

Run:

```bash
./gradlew :game:blockblast:allTests
```

Expected: all tests pass.

The existing procedural `DefaultBlockBlastAudioPlayer` remains wired in Metro
until Task 5. This keeps every intermediate commit buildable while the new
controller is tested directly.

- [ ] **Step 5: Commit the session policy**

```bash
git add game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/data/audio \
  game/blockblast/src/commonTest/kotlin/ge/yet/game/blockblast/data/audio/BlockBlastAudioTest.kt
git commit -m "feat: gate Block Blast audio by session state"
```

### Task 4: Move Android and iOS playback into Block Blast

**Files:**
- Create: `game/blockblast/src/androidMain/kotlin/ge/yet/game/blockblast/data/audio/AndroidBlockBlastPlatformAudioPlayer.kt`
- Create: `game/blockblast/src/androidMain/kotlin/ge/yet/game/blockblast/di/AndroidBlockBlastAudioBindings.kt`
- Create: `game/blockblast/src/nativeMain/kotlin/ge/yet/game/blockblast/data/audio/NativeBlockBlastPlatformAudioPlayer.kt`
- Create: `game/blockblast/src/nativeMain/kotlin/ge/yet/game/blockblast/di/NativeBlockBlastAudioBindings.kt`
- Modify: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/di/ComposeAudioFileProvider.kt`

- [ ] **Step 1: Compile the unchanged platform sources to establish a baseline**

Run:

```bash
./gradlew :game:blockblast:compileAndroidMain :game:blockblast:compileKotlinIosSimulatorArm64
```

Expected: both tasks pass. The new platform boundary is not wired into the
production session graph yet.

- [ ] **Step 2: Make the resource provider private to Block Blast**

Remove `AudioFileProvider` inheritance/import from `ComposeAudioFileProvider`
and retain concrete internal methods:

```kotlin
@SingleIn(AppScope::class)
@Inject
internal class ComposeAudioFileProvider {
    fun path(filename: String): String =
        "composeResources/ge.yet.game.blockblast.generated.resources/files/audio/$filename"

    suspend fun bytes(filename: String): ByteArray? = runCatching {
        Res.readBytes("files/audio/$filename")
    }.getOrNull()
}
```

- [ ] **Step 3: Move the Android implementation and bind it in session scope**

Copy the proven `SoundPool`/`MediaPlayer` state machine from
`core/data/.../AndroidPlatformSoundPlayer.kt`, rename it to
`AndroidBlockBlastPlatformAudioPlayer`, change `playSound` to `playVoice`, and
inject the concrete `ComposeAudioFileProvider`. Keep `MusicState`, async
preparation guards, ready-ID tracking, error listeners, music volume `0.4f`, and
the shared `nextTrackIndex` unchanged.

Bind it only on Android:

```kotlin
@ContributesTo(MiniAppSessionScope::class)
@BindingContainer
internal abstract class AndroidBlockBlastAudioBindings {
    @Binds
    abstract val AndroidBlockBlastPlatformAudioPlayer.bindBlockBlastPlatformAudioPlayer:
        BlockBlastPlatformAudioPlayer
}
```

Annotate the implementation with `@Inject` and
`@SingleIn(MiniAppSessionScope::class)`.

- [ ] **Step 4: Move the iOS implementation and bind it in session scope**

Copy the proven `AVAudioPlayer` implementation from
`core/data/.../NativePlatformSoundPlayer.kt`, rename it to
`NativeBlockBlastPlatformAudioPlayer`, change `playSound` to `playVoice`, and
inject `ComposeAudioFileProvider` plus `ComponentContext`. Create its scope with
`componentContext.coroutineScope()` so async reads and playlist jobs die with
the session. Preserve generation checks, `Dispatchers.Default` resource/temp
file work, `Dispatchers.Main` player access, the SFX cache/miss set, volume
`0.4f`, and `AVAudioSessionCategoryPlayback` setup.

Bind it only in `nativeMain`:

```kotlin
@ContributesTo(MiniAppSessionScope::class)
@BindingContainer
internal abstract class NativeBlockBlastAudioBindings {
    @Binds
    abstract val NativeBlockBlastPlatformAudioPlayer.bindBlockBlastPlatformAudioPlayer:
        BlockBlastPlatformAudioPlayer
}
```

Annotate the implementation with `@Inject` and
`@SingleIn(MiniAppSessionScope::class)`.

- [ ] **Step 5: Compile both platform implementations**

Run:

```bash
./gradlew :game:blockblast:compileAndroidMain :game:blockblast:compileKotlinIosSimulatorArm64
```

Expected: both tasks pass without introducing a new external dependency.

- [ ] **Step 6: Commit private platform playback**

```bash
git add game/blockblast/src/androidMain game/blockblast/src/nativeMain \
  game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/di/ComposeAudioFileProvider.kt
git commit -m "feat: restore Block Blast native file playback"
```

### Task 5: Wire the private player into the retained session graph

**Files:**
- Rename: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/data/audio/DefaultBlockBlastFileAudioPlayer.kt` to `DefaultBlockBlastAudioPlayer.kt`
- Modify: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/data/audio/BlockBlastAudio.kt`
- Modify: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/di/BlockBlastAppBindings.kt`
- Modify: `game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/di/BlockBlastSessionBindings.kt`
- Modify: `game/blockblast/src/commonTest/kotlin/ge/yet/game/blockblast/data/audio/BlockBlastAudioTest.kt`
- Modify: `game/blockblast/src/commonTest/kotlin/ge/yet/game/blockblast/di/BlockBlastSessionGraphTest.kt`

- [ ] **Step 1: Add a graph-level lifecycle assertion for the private player**

Expose `BlockBlastAudioPlayer` from `InspectableBlockBlastSessionGraph`, provide
a recording `BlockBlastPlatformAudioPlayer` from `BlockBlastGraphTestBindings`,
and assert the same session binding is reused and released once:

```kotlin
@Test
fun private_audio_player_is_session_scoped_and_released_with_the_child_graph() = runTest(dispatcher) {
    val appGraph = createGraph<BlockBlastPluginTestGraph>()
    val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
    val graph = appGraph.inspectableSessionFactory.createInspectable(
        TestMiniAppSessionContext(
            lifecycle.componentContext,
            MutableMiniAppVisibilitySource(),
            RecordingMiniAppSessionHost(),
        ),
    )

    assertSame(graph.audioPlayer, graph.audioPlayer)
    lifecycle.destroy()
    runCurrent()

    assertEquals(1, appGraph.platformAudio.releaseCount)
    appGraph.appScope.cancel()
}
```

The test graph exposes the recording platform instance as `platformAudio` so
the assertion does not depend on native APIs.

- [ ] **Step 2: Run the graph test and verify it fails**

Run:

```bash
./gradlew :game:blockblast:allTests
```

Expected: graph construction fails until the new controller provider is wired.

- [ ] **Step 3: Replace the procedural session binding**

Remove `MiniAppAudio` from `BlockBlastSessionBindings` and provide the private
controller from session inputs:

```kotlin
@Provides
@SingleIn(MiniAppSessionScope::class)
internal fun provideBlockBlastAudioPlayer(
    platform: BlockBlastPlatformAudioPlayer,
    feedback: FeedbackPreferences,
    visibility: MiniAppVisibilitySource,
    componentContext: ComponentContext,
): BlockBlastAudioPlayer = DefaultBlockBlastAudioPlayer(
    platform = platform,
    preferences = feedback,
    visibility = visibility,
    componentContext = componentContext,
)
```

Remove the obsolete `ComposeAudioFileProvider.bindAudioFileProvider` binding
from `BlockBlastAppBindings`; platform implementations inject its concrete type.

Delete the old procedural `BlockBlastAudio` declaration and
`DefaultBlockBlastAudioPlayer` from `BlockBlastAudio.kt`. Delete the old
procedural assertions, `RecordingMiniAppAudio` fixture, and procedural imports
from `BlockBlastAudioTest.kt`; keep the asset and session-policy tests introduced
in Tasks 2–3. Rename `DefaultBlockBlastFileAudioPlayer.kt` and its class to
`DefaultBlockBlastAudioPlayer` after deleting the old class, then update the
session-policy test fixture to instantiate the final name.

- [ ] **Step 4: Replace the old procedural lifecycle graph test**

Delete `destroying_the_child_lifecycle_stops_session_audio_once` and the local
`RecordingMiniAppAudio` fixture. Keep generic `TestMiniAppSessionContext` audio
at its default no-op value because Block Blast must no longer call it.

- [ ] **Step 5: Run graph tests and platform compilation**

Run:

```bash
./gradlew :game:blockblast:allTests \
  :game:blockblast:compileAndroidMain \
  :game:blockblast:compileKotlinIosSimulatorArm64
```

Expected: all tasks pass; no Block Blast production source imports procedural
program, preset, `SfxName`, or `MiniAppAudio` symbols.

- [ ] **Step 6: Commit session graph wiring**

```bash
git add game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/data/audio \
  game/blockblast/src/commonMain/kotlin/ge/yet/game/blockblast/di \
  game/blockblast/src/commonTest/kotlin/ge/yet/game/blockblast/data/audio/BlockBlastAudioTest.kt \
  game/blockblast/src/commonTest/kotlin/ge/yet/game/blockblast/di/BlockBlastSessionGraphTest.kt
git commit -m "refactor: scope Block Blast file audio to its session"
```

### Task 6: Remove shared legacy file-audio infrastructure and Root forwarding

**Files:**
- Delete: `core/domain/src/commonMain/kotlin/ge/yet/game/domain/repository/AudioRepository.kt`
- Delete: `core/domain/src/commonMain/kotlin/ge/yet/game/domain/repository/AudioFileProvider.kt`
- Delete: `core/data/src/commonMain/kotlin/ge/yet/game/data/repository/DefaultAudioRepository.kt`
- Delete: `core/data/src/commonMain/kotlin/ge/yet/game/data/platform/PlatformSoundPlayer.kt`
- Delete: `core/data/src/androidMain/kotlin/ge/yet/game/data/platform/AndroidPlatformSoundPlayer.kt`
- Delete: `core/data/src/nativeMain/kotlin/ge/yet/game/data/platform/NativePlatformSoundPlayer.kt`
- Delete: `core/data/src/commonTest/kotlin/ge/yet/game/data/repository/DefaultAudioRepositoryTest.kt`
- Modify: `core/data/src/commonMain/kotlin/ge/yet/game/data/di/DataBindings.kt`
- Modify: `core/data/src/androidMain/kotlin/ge/yet/game/data/di/AndroidDataBindings.kt`
- Modify: `core/data/src/nativeMain/kotlin/ge/yet/game/data/di/NativeDataBindings.kt`
- Modify: `feature/root/src/commonMain/kotlin/ge/yet/game/feature/root/DefaultRootComponent.kt`
- Modify: `feature/root/src/commonTest/kotlin/ge/yet/game/feature/root/DefaultRootComponentTest.kt`

- [ ] **Step 1: Change the Root test to assert lifecycle ownership is absent**

Delete `RecordingAudio`, its `Setup.audio` field, and
`app_start_and_stop_still_forward_to_audio_without_recreating_session`. Add a
replacement proving Root lifecycle changes preserve the running session without
a legacy audio collaborator:

```kotlin
@Test
fun app_start_and_stop_do_not_recreate_the_running_session() = runTest {
    val setup = build()
    setup.lifecycle.resume()
    setup.play(FIRST_ID)

    setup.lifecycle.stop()
    setup.lifecycle.resume()
    runCurrent()

    assertEquals(1, setup.firstPlugin.createCount)
    assertIs<RootComponent.Child.RunningMiniApp>(setup.component.stack.value.active.instance)
}
```

Remove the `audio` constructor argument when building `DefaultRootComponent`.

- [ ] **Step 2: Remove AudioRepository from Root production code**

Delete its import, constructor/factory properties and arguments, and these
legacy forwarding statements:

```kotlin
rootScope.launch { audio.onAppForeground() }
rootScope.launch { audio.onAppBackground() }
```

Keep `runtimeCoordinator.setForeground(true/false)` unchanged because it owns
generic MiniApp visibility and procedural audio suppression.

- [ ] **Step 3: Remove shared DI bindings and implementations**

Delete the seven obsolete files listed above. Remove
`DefaultAudioRepository.bindAudioRepository` from `DataBindings`, remove the
Android platform sound-player binding from `AndroidDataBindings`, and remove the
native platform sound-player binding from `NativeDataBindings`. Update their
KDoc so it only names Settings, vibration, and store-review platform bindings.

- [ ] **Step 4: Prove no shared legacy symbol remains**

Run:

```bash
rg -n 'AudioRepository|AudioFileProvider|PlatformSoundPlayer|DefaultAudioRepository' \
  core feature composeApp game miniapp --glob '*.kt'
```

Expected: no matches. `BlockBlastPlatformAudioPlayer` is intentionally a
different game-private symbol and may appear.

- [ ] **Step 5: Run affected module tests**

Run:

```bash
./gradlew :core:domain:allTests :core:data:allTests :feature:root:allTests :game:blockblast:allTests
```

Expected: all tasks pass.

- [ ] **Step 6: Commit legacy removal**

```bash
git add core/domain core/data feature/root
git commit -m "refactor: remove shared bundled audio infrastructure"
```

### Task 7: Align architecture and contributor documentation

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/miniapp/audio/getting-started.md`
- Modify: `.agents/skills/miniapp-procedural-audio/SKILL.md`

- [ ] **Step 1: Update the canonical architecture map**

Change `AGENTS.md` so:

- `:core:data` no longer claims reusable audio playback;
- `:game:blockblast` explicitly owns private bundled MP3 playback;
- the MiniApp audio section says Block Blast is the sole private exception and
  does not demonstrate a contributor-facing asset API;
- contributors are told to keep using procedural `MiniAppAudio` and to request
  an ADR if a second game needs bundled files.

- [ ] **Step 2: Add the same boundary to human and agent authoring guidance**

Add this explicit note to `getting-started.md` and the repository-local skill:

```markdown
Block Blast's bundled MP3 player is a private compatibility exception, not a
public MiniApp capability or copyable contributor pattern. New MiniApps author
audio through `MiniAppAudio` and `:miniapp:audio-presets`. If a second reviewed
MiniApp genuinely requires bundled audio, propose a separate architecture
decision instead of importing Block Blast internals.
```

- [ ] **Step 3: Verify stale architecture claims are gone**

Run:

```bash
rg -n 'Block Blast.*procedural|reusable audio playback|AudioRepository|AudioFileProvider' \
  AGENTS.md docs .agents/skills --glob '*.md'
```

Expected: remaining matches only discuss removal/history or explicitly describe
the private exception; no text says Block Blast uses procedural music/SFX.

- [ ] **Step 4: Commit documentation**

```bash
git add AGENTS.md docs/miniapp/audio/getting-started.md \
  .agents/skills/miniapp-procedural-audio/SKILL.md
git commit -m "docs: document Block Blast bundled audio exception"
```

### Task 8: Run complete verification and perform native playback QA

**Files:**
- Modify if verification exposes a scoped defect: only files already listed in Tasks 1–7

- [ ] **Step 1: Run formatting and static diff checks**

Run:

```bash
git diff --check
rg -n 'ge\.yet\.game\.miniapp\.audio|audio\.presets' \
  game/blockblast/src/commonMain --glob '*.kt'
```

Expected: `git diff --check` succeeds and the Block Blast production-source
search returns no procedural imports.

- [ ] **Step 2: Run all affected common tests**

Run:

```bash
./gradlew :core:domain:allTests \
  :core:data:allTests \
  :feature:root:allTests \
  :game:blockblast:allTests
```

Expected: all tasks pass.

- [ ] **Step 3: Compile both Block Blast platforms and both application hosts**

Run:

```bash
./gradlew :game:blockblast:compileAndroidMain \
  :game:blockblast:compileKotlinIosSimulatorArm64 \
  :composeApp:compileAndroidMain \
  :composeApp:linkDebugFrameworkIosSimulatorArm64 \
  :androidApp:assembleDebug
```

Expected: all tasks pass.

- [ ] **Step 4: Verify Android behavior manually**

On an emulator or device, confirm all of the following:

1. A playable Block Blast round starts one of the three old music tracks.
2. Every feedback tier plays its matching old voice clip.
3. Disabling Music stops music and enabling it resumes the active request.
4. Disabling Sounds suppresses voices without stopping music.
5. Opening Settings, backgrounding, and leaving Block Blast silence audio.
6. Returning from Settings/background resumes requested music, while returning
   after game-over does not.

- [ ] **Step 5: Verify iOS behavior manually**

Repeat the six checks on the iPhone 11 Pro Max simulator and, when available,
the iPhone 16 Pro device. Confirm Xcode reports no uncaught exception and no
audio continues after the retained session is destroyed.

- [ ] **Step 6: Record verification and commit any final scoped correction**

If no source correction is required, leave the tree clean and report the exact
commands and manual cases completed. If verification required a correction,
rerun the failed command plus the complete Task 8 command set, inspect
`git diff --name-only`, then stage only the known scoped paths and commit the
correction:

```bash
git add game/blockblast/src core/domain/src core/data/src feature/root/src \
  AGENTS.md docs/miniapp/audio/getting-started.md \
  .agents/skills/miniapp-procedural-audio/SKILL.md
git commit -m "fix: complete Block Blast file audio migration"
```
