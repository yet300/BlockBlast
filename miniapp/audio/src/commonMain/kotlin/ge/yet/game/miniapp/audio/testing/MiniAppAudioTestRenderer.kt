package ge.yet.game.miniapp.audio.testing

import ge.yet.game.miniapp.audio.AudioDiagnostic
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.internal.OfflineAudioRenderResult
import ge.yet.game.miniapp.audio.internal.OfflineAudioRenderer
import ge.yet.game.miniapp.audio.internal.OfflineAudioRequest

@RequiresOptIn(
    message = "The deterministic renderer is intended for tests and acoustic verification.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalMiniAppAudioTestingApi

@ExperimentalMiniAppAudioTestingApi
sealed interface AudioTestRenderResult {
    class Success(val pcm: AudioTestPcm) : AudioTestRenderResult

    class Failure(diagnostics: List<AudioDiagnostic>) : AudioTestRenderResult {
        val diagnostics: List<AudioDiagnostic> = diagnostics.toList()
    }
}

@ExperimentalMiniAppAudioTestingApi
class AudioTestPcm internal constructor(
    left: FloatArray,
    right: FloatArray,
    val peak: Float,
    val rms: Double,
    val quantizedPcmHash: Long,
) {
    private val leftSamples = left.copyOf()
    private val rightSamples = right.copyOf()

    val left: FloatArray get() = leftSamples.copyOf()
    val right: FloatArray get() = rightSamples.copyOf()
    val frameCount: Int get() = leftSamples.size
}

@ExperimentalMiniAppAudioTestingApi
object MiniAppAudioTestRenderer {
    fun render(
        program: AudioProgram,
        sampleRate: Int,
        frameCount: Int,
    ): AudioTestRenderResult = when (
        val result = OfflineAudioRenderer.render(program, OfflineAudioRequest(sampleRate, frameCount))
    ) {
        is OfflineAudioRenderResult.Failure -> AudioTestRenderResult.Failure(result.diagnostics)
        is OfflineAudioRenderResult.Success -> AudioTestRenderResult.Success(
            AudioTestPcm(
                left = result.audio.left,
                right = result.audio.right,
                peak = result.audio.peak,
                rms = result.audio.rms,
                quantizedPcmHash = result.audio.quantizedPcmHash(),
            ),
        )
    }
}
