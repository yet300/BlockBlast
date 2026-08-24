package ge.yet.game.blockblast.data.audio

import com.app.common.decompose.coroutineScope
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import ge.yet.game.blockblast.domain.model.FeedbackType
import ge.yet.game.domain.repository.FeedbackPreferences
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal class DefaultBlockBlastFileAudioPlayer(
    private val platform: BlockBlastPlatformAudioPlayer,
    private val preferences: FeedbackPreferences,
    private val visibility: MiniAppVisibilitySource,
    componentContext: ComponentContext,
) : BlockBlastAudioPlayer {
    private val requestedMusic = MutableStateFlow(false)
    private var destroyed = false

    init {
        componentContext.coroutineScope().launch {
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
