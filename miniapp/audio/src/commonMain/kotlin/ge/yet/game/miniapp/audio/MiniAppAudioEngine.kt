package ge.yet.game.miniapp.audio

import com.arkivanov.essenty.lifecycle.Lifecycle
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppVisibilitySource

interface MiniAppAudioEngine {
    fun openSession(
        id: MiniAppId,
        sessionKey: Long,
        lifecycle: Lifecycle,
        visibility: MiniAppVisibilitySource,
    ): MiniAppAudio

    fun closeSession(id: MiniAppId, sessionKey: Long)
}
