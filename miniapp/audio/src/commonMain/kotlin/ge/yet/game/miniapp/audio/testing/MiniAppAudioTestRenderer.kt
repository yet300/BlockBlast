package ge.yet.game.miniapp.audio.testing

import ge.yet.game.miniapp.audio.AudioCompilationResult
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioDiagnostic
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.audio.compile
import ge.yet.game.miniapp.audio.internal.AudioRuntimeCommandOutcome
import ge.yet.game.miniapp.audio.internal.AudioSessionPolicy
import ge.yet.game.miniapp.audio.internal.OfflineAudioRenderResult
import ge.yet.game.miniapp.audio.internal.OfflineAudioRenderer
import ge.yet.game.miniapp.audio.internal.OfflineAudioRequest
import ge.yet.game.miniapp.audio.internal.RealtimeAudioRenderer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@RequiresOptIn(
    message = "The deterministic renderer is intended for tests and acoustic verification.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalMiniAppAudioTestingApi

@ExperimentalMiniAppAudioTestingApi
data class AudioTestControlValue(
    val name: AudioControlName,
    val value: Float,
)

@ExperimentalMiniAppAudioTestingApi
data class AudioTestSfxTrigger(
    val name: SfxName,
    val frameOffset: Int,
)

@ExperimentalMiniAppAudioTestingApi
data class AudioTestRenderRequest(
    val sampleRate: Int,
    val frameCount: Int,
    val includeMusic: Boolean = true,
    val controls: List<AudioTestControlValue> = emptyList(),
    val sfxTriggers: List<AudioTestSfxTrigger> = emptyList(),
)

@ExperimentalMiniAppAudioTestingApi
enum class AudioTestRequestErrorCode {
    SAMPLE_RATE_OUT_OF_RANGE,
    FRAME_COUNT_OUT_OF_RANGE,
    CONTROL_OVERRIDE_LIMIT_EXCEEDED,
    SFX_TRIGGER_LIMIT_EXCEEDED,
    DUPLICATE_CONTROL,
    UNKNOWN_CONTROL,
    UNKNOWN_SFX,
    NON_FINITE_CONTROL_VALUE,
    CONTROL_VALUE_OUT_OF_RANGE,
    FRAME_OFFSET_OUT_OF_RANGE,
}

@ExperimentalMiniAppAudioTestingApi
data class AudioTestRequestError(
    val code: AudioTestRequestErrorCode,
    val path: String,
    val message: String,
)

@ExperimentalMiniAppAudioTestingApi
sealed interface AudioTestRenderResult {
    class Success(val pcm: AudioTestPcm) : AudioTestRenderResult

    class Failure(
        diagnostics: List<AudioDiagnostic> = emptyList(),
        requestErrors: List<AudioTestRequestError> = emptyList(),
    ) : AudioTestRenderResult {
        val diagnostics: List<AudioDiagnostic> = diagnostics.toList()
        val requestErrors: List<AudioTestRequestError> = requestErrors.toList()
    }
}

@ExperimentalMiniAppAudioTestingApi
class AudioTestPcm internal constructor(
    val sampleRate: Int,
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
    @ExperimentalMiniAppAudioTestingApi
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
                sampleRate = sampleRate,
                left = result.audio.left,
                right = result.audio.right,
                peak = result.audio.peak,
                rms = result.audio.rms,
                quantizedPcmHash = result.audio.quantizedPcmHash(),
            ),
        )
    }

    @ExperimentalMiniAppAudioTestingApi
    fun render(
        program: AudioProgram,
        request: AudioTestRenderRequest,
    ): AudioTestRenderResult {
        val collectionErrors = validateCollectionBounds(request)
        if (collectionErrors.isNotEmpty()) {
            return AudioTestRenderResult.Failure(requestErrors = collectionErrors)
        }
        val renderRequest = request.copy(
            controls = request.controls.toList(),
            sfxTriggers = request.sfxTriggers.toList(),
        )
        val requestErrors = validateRequest(program, renderRequest)
        if (requestErrors.isNotEmpty()) return AudioTestRenderResult.Failure(requestErrors = requestErrors)

        val compiled = when (val compilation = program.compile()) {
            is AudioCompilationResult.Failure -> return AudioTestRenderResult.Failure(compilation.diagnostics)
            is AudioCompilationResult.Success -> compilation.program
        }

        val blockCapacity = minOf(renderRequest.frameCount, TEST_RENDER_BLOCK_FRAMES)
        val renderer = RealtimeAudioRenderer(renderRequest.sampleRate, blockCapacity)
        if (!renderRequest.includeMusic) {
            renderer.updatePolicy(
                AudioSessionPolicy(
                    musicGain = 0f,
                    acceptsNewSfx = true,
                    schedulingPaused = false,
                ),
            )
        }
        check(renderer.playMusic(compiled) == AudioRuntimeCommandOutcome.APPLIED)
        renderRequest.controls.forEach { control ->
            check(renderer.setControl(control.name, control.value) == AudioRuntimeCommandOutcome.APPLIED)
        }

        val orderedTriggers = renderRequest.sfxTriggers.withIndex()
            .sortedWith(compareBy<IndexedValue<AudioTestSfxTrigger>>({ it.value.frameOffset }, { it.index }))
        val left = FloatArray(renderRequest.frameCount)
        val right = FloatArray(renderRequest.frameCount)
        val blockLeft = FloatArray(blockCapacity)
        val blockRight = FloatArray(blockCapacity)
        var renderedFrames = 0
        var triggerIndex = 0
        while (renderedFrames < renderRequest.frameCount) {
            while (
                triggerIndex < orderedTriggers.size &&
                orderedTriggers[triggerIndex].value.frameOffset == renderedFrames
            ) {
                val outcome = renderer.playSfx(compiled, orderedTriggers[triggerIndex].value.name)
                check(
                    outcome == AudioRuntimeCommandOutcome.APPLIED ||
                        outcome == AudioRuntimeCommandOutcome.FORCED_VOICE_SHEDDING,
                )
                triggerIndex++
            }

            val nextTriggerFrame = orderedTriggers.getOrNull(triggerIndex)?.value?.frameOffset
                ?: renderRequest.frameCount
            val framesUntilTrigger = nextTriggerFrame - renderedFrames
            val blockFrames = minOf(blockCapacity, framesUntilTrigger, renderRequest.frameCount - renderedFrames)
            check(blockFrames > 0) { "Validated test render request did not make forward progress" }
            renderer.render(blockLeft, blockRight, blockFrames)
            blockLeft.copyInto(left, destinationOffset = renderedFrames, endIndex = blockFrames)
            blockRight.copyInto(right, destinationOffset = renderedFrames, endIndex = blockFrames)
            renderedFrames += blockFrames
        }

        return AudioTestRenderResult.Success(
            AudioTestPcm(
                sampleRate = renderRequest.sampleRate,
                left = left,
                right = right,
                peak = calculatePeak(left, right),
                rms = calculateRms(left, right),
                quantizedPcmHash = quantizedPcmHash(left, right),
            ),
        )
    }
}

@OptIn(ExperimentalMiniAppAudioTestingApi::class)
private fun validateRequest(
    program: AudioProgram,
    request: AudioTestRenderRequest,
): List<AudioTestRequestError> {
    val collectionErrors = validateCollectionBounds(request)
    if (collectionErrors.isNotEmpty()) return collectionErrors

    return buildList {
        if (request.sampleRate !in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE) {
            add(
                requestError(
                    AudioTestRequestErrorCode.SAMPLE_RATE_OUT_OF_RANGE,
                    "sampleRate",
                    "Sample rate must be in $MIN_SAMPLE_RATE..$MAX_SAMPLE_RATE",
                ),
            )
        }
        val maximumFrames = request.sampleRate.toLong() * MAX_RENDER_SECONDS
        if (request.frameCount <= 0 || request.frameCount.toLong() > maximumFrames) {
            add(
                requestError(
                    AudioTestRequestErrorCode.FRAME_COUNT_OUT_OF_RANGE,
                    "frameCount",
                    "Frame count must be positive and no longer than $MAX_RENDER_SECONDS seconds",
                ),
            )
        }

        val controlsByName = program.controls.associateBy { it.name }
        val seenControls = mutableSetOf<AudioControlName>()
        request.controls.forEachIndexed { index, override ->
            val path = "controls[$index]"
            if (!seenControls.add(override.name)) {
                add(
                    requestError(
                        AudioTestRequestErrorCode.DUPLICATE_CONTROL,
                        "$path.name",
                        "Control '${override.name.value}' has more than one override",
                    ),
                )
            }
            val declaration = controlsByName[override.name]
            if (declaration == null) {
                add(
                    requestError(
                        AudioTestRequestErrorCode.UNKNOWN_CONTROL,
                        "$path.name",
                        "Unknown control '${override.name.value}'",
                    ),
                )
            } else if (!override.value.isFinite()) {
                add(
                    requestError(
                        AudioTestRequestErrorCode.NON_FINITE_CONTROL_VALUE,
                        "$path.value",
                        "Control value must be finite",
                    ),
                )
            } else if (override.value !in declaration.range) {
                add(
                    requestError(
                        AudioTestRequestErrorCode.CONTROL_VALUE_OUT_OF_RANGE,
                        "$path.value",
                        "Control '${override.name.value}' must be in ${declaration.range}",
                    ),
                )
            }
        }

        val sfxNames = program.soundEffects.mapTo(mutableSetOf()) { it.name }
        request.sfxTriggers.forEachIndexed { index, trigger ->
            val path = "sfxTriggers[$index]"
            if (trigger.name !in sfxNames) {
                add(
                    requestError(
                        AudioTestRequestErrorCode.UNKNOWN_SFX,
                        "$path.name",
                        "Unknown SFX '${trigger.name.value}'",
                    ),
                )
            }
            if (trigger.frameOffset !in 0 until request.frameCount) {
                add(
                    requestError(
                        AudioTestRequestErrorCode.FRAME_OFFSET_OUT_OF_RANGE,
                        "$path.frameOffset",
                        "SFX frame offset must be in 0 until frameCount",
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMiniAppAudioTestingApi::class)
private fun validateCollectionBounds(request: AudioTestRenderRequest): List<AudioTestRequestError> {
    if (request.controls.size > MAX_TEST_CONTROL_OVERRIDES) {
        return listOf(
            requestError(
                AudioTestRequestErrorCode.CONTROL_OVERRIDE_LIMIT_EXCEEDED,
                "controls",
                "Test render accepts at most $MAX_TEST_CONTROL_OVERRIDES control overrides",
            ),
        )
    }
    if (request.sfxTriggers.size > MAX_TEST_SFX_TRIGGERS) {
        return listOf(
            requestError(
                AudioTestRequestErrorCode.SFX_TRIGGER_LIMIT_EXCEEDED,
                "sfxTriggers",
                "Test render accepts at most $MAX_TEST_SFX_TRIGGERS SFX triggers",
            ),
        )
    }
    return emptyList()
}

@OptIn(ExperimentalMiniAppAudioTestingApi::class)
private fun requestError(
    code: AudioTestRequestErrorCode,
    path: String,
    message: String,
) = AudioTestRequestError(code, path, message)

private fun calculatePeak(left: FloatArray, right: FloatArray): Float {
    var peak = 0f
    for (index in left.indices) peak = maxOf(peak, abs(left[index]), abs(right[index]))
    return peak
}

private fun calculateRms(left: FloatArray, right: FloatArray): Double {
    var sum = 0.0
    for (index in left.indices) sum += left[index] * left[index] + right[index] * right[index]
    return sqrt(sum / (left.size * 2.0))
}

private fun quantizedPcmHash(left: FloatArray, right: FloatArray): Long {
    var hash = -3750763034362895579L
    for (index in left.indices) {
        hash = hashQuantizedSample(hash, left[index])
        hash = hashQuantizedSample(hash, right[index])
    }
    return hash
}

private fun hashQuantizedSample(initial: Long, sample: Float): Long {
    val quantized = (sample.coerceIn(-1f, 1f) * 32_767f).roundToInt()
    var hash = (initial xor (quantized and 0xFF).toLong()) * 1_099_511_628_211L
    hash = (hash xor ((quantized ushr 8) and 0xFF).toLong()) * 1_099_511_628_211L
    return hash
}

private const val MIN_SAMPLE_RATE = 8_000
private const val MAX_SAMPLE_RATE = 192_000
private const val MAX_RENDER_SECONDS = 60L
private const val MAX_TEST_CONTROL_OVERRIDES = 32
private const val MAX_TEST_SFX_TRIGGERS = 256
private const val TEST_RENDER_BLOCK_FRAMES = 512
