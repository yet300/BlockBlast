package ge.yet.sample.counter

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.doOnDestroy
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.audio.AudioCommandResult
import ge.yet.game.miniapp.audio.MiniAppAudio
import kotlinx.coroutines.flow.StateFlow

interface CounterComponent {
    val model: Value<Model>
    val visibility: StateFlow<MiniAppVisibility>

    fun onIncrementClicked()
    fun onPlayMusicClicked()
    fun onStopMusicClicked()
    fun onIntensityChanged(value: Float)
    fun onSoundEffectClicked(effect: SoundEffect)

    data class Model(
        val count: Int = 0,
        val musicPlaying: Boolean = false,
        val intensity: Float = DEFAULT_INTENSITY,
        val lastAudioResult: AudioCommandResult? = null,
    )

    enum class SoundEffect { PLACEMENT, SUCCESS, EXPLOSION, POWER_UP }

    companion object {
        const val DEFAULT_INTENSITY = 0.5f
    }
}

internal class DefaultCounterComponent(
    componentContext: ComponentContext,
    visibilitySource: MiniAppVisibilitySource,
    private val audio: MiniAppAudio,
) : CounterComponent, ComponentContext by componentContext {
    private val mutableModel = MutableValue(CounterComponent.Model())
    override val model: Value<CounterComponent.Model> = mutableModel
    override val visibility: StateFlow<MiniAppVisibility> = visibilitySource.visibility

    internal var destroyCount: Int = 0
        private set

    init {
        lifecycle.doOnDestroy { destroyCount += 1 }
    }

    override fun onIncrementClicked() {
        mutableModel.update { current ->
            current.copy(count = current.count + 1)
        }
        onSoundEffectClicked(CounterComponent.SoundEffect.PLACEMENT)
    }

    override fun onPlayMusicClicked() = acceptAudioResult(audio.playMusic(CounterAudio.program)) {
        copy(musicPlaying = true)
    }

    override fun onStopMusicClicked() = acceptAudioResult(audio.stopMusic()) {
        copy(musicPlaying = false)
    }

    override fun onIntensityChanged(value: Float) =
        acceptAudioResult(audio.setControl(CounterAudio.Intensity, value)) {
            copy(intensity = value)
        }

    override fun onSoundEffectClicked(effect: CounterComponent.SoundEffect) {
        val result = audio.playSfx(CounterAudio.program, CounterAudio.sfxName(effect))
        mutableModel.update { it.copy(lastAudioResult = result) }
    }

    private fun acceptAudioResult(
        result: AudioCommandResult,
        update: CounterComponent.Model.() -> CounterComponent.Model,
    ) {
        mutableModel.update { current ->
            if (result === AudioCommandResult.Accepted) {
                current.update().copy(lastAudioResult = result)
            } else {
                current.copy(lastAudioResult = result)
            }
        }
    }
}
