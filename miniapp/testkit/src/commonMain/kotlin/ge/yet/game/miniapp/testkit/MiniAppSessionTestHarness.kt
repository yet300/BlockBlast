package ge.yet.game.miniapp.testkit

import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.audio.MiniAppAudio

/**
 * Owns the lifecycle and default host inputs needed to exercise one MiniApp session.
 */
class MiniAppSessionTestHarness(
    initialVisibility: MiniAppVisibility = MiniAppVisibility.ACTIVE,
    val storage: MiniAppStorage = MutableMiniAppStorage(),
    val audio: MiniAppAudio = NoopMiniAppAudio,
    val host: MiniAppSessionHost = RecordingMiniAppSessionHost(),
) {
    private val lifecycleHarness = MiniAppLifecycleHarness()
    val visibility = MutableMiniAppVisibilitySource(initialVisibility)
    val context: TestMiniAppSessionContext = TestMiniAppSessionContext(
        componentContext = lifecycleHarness.componentContext,
        visibility = visibility,
        host = host,
        storage = storage,
        audio = audio,
    )

    fun resume() = lifecycleHarness.resume()

    fun stop() = lifecycleHarness.stop()

    fun destroy() = lifecycleHarness.destroy()
}

inline fun <T> withMiniAppSession(
    initialVisibility: MiniAppVisibility = MiniAppVisibility.ACTIVE,
    storage: MiniAppStorage = MutableMiniAppStorage(),
    audio: MiniAppAudio = NoopMiniAppAudio,
    host: MiniAppSessionHost = RecordingMiniAppSessionHost(),
    block: (MiniAppSessionTestHarness) -> T,
): T {
    val harness = MiniAppSessionTestHarness(
        initialVisibility = initialVisibility,
        storage = storage,
        audio = audio,
        host = host,
    )
    return try {
        block(harness)
    } finally {
        harness.destroy()
    }
}
