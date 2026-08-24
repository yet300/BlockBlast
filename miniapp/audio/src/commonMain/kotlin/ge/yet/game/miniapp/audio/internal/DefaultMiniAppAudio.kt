package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.audio.AudioCommandRejection
import ge.yet.game.miniapp.audio.AudioCommandResult
import ge.yet.game.miniapp.audio.AudioCompilationResult
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioDuration
import ge.yet.game.miniapp.audio.AudioLookupResult
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.audio.compile
import ge.yet.game.miniapp.audio.control
import ge.yet.game.miniapp.audio.sfx
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

internal class DefaultMiniAppAudio(
    private val backend: PlatformAudioSinkSession?,
    private val diagnostics: AudioDiagnostics,
    initialVisibility: MiniAppVisibility,
    initialMusicEnabled: Boolean,
    initialSfxEnabled: Boolean,
    private val onClosed: () -> Unit,
) : MiniAppAudio {
    private val state = MutableStateFlow(
        State(
            visibility = initialVisibility,
            musicEnabled = initialMusicEnabled,
            sfxEnabled = initialSfxEnabled,
        ),
    )

    init {
        updateBackendPolicy()
    }

    override fun playMusic(program: AudioProgram): AudioCommandResult {
        rejectedWhenUnavailable()?.let { return it }
        val compiled = when (val result = program.compile()) {
            is AudioCompilationResult.Failure -> return AudioCommandResult.Rejected(
                AudioCommandRejection.INVALID_PROGRAM,
                result.diagnostics,
            )
            is AudioCompilationResult.Success -> result.program
        }
        return submit { it.playMusic(compiled) }.also { result ->
            if (result === AudioCommandResult.Accepted) updateState { it.copy(currentMusic = program) }
        }
    }

    override fun stopMusic(fadeOut: AudioDuration): AudioCommandResult {
        rejectedWhenUnavailable()?.let { return it }
        val fadeFrames = (fadeOut.seconds * requireNotNull(backend).sampleRate).roundToInt()
        return submit { it.stopMusic(fadeFrames) }.also { result ->
            if (result === AudioCommandResult.Accepted) updateState { it.copy(currentMusic = null) }
        }
    }

    override fun playSfx(program: AudioProgram, name: SfxName): AudioCommandResult {
        rejectedWhenUnavailable()?.let { return it }
        if (!policy(state.value).acceptsNewSfx) {
            return AudioCommandResult.Rejected(AudioCommandRejection.PLAYBACK_SUPPRESSED)
        }
        if (program.sfx(name) is AudioLookupResult.Missing) {
            return AudioCommandResult.Rejected(AudioCommandRejection.UNKNOWN_SFX)
        }
        val compiled = when (val result = program.compile()) {
            is AudioCompilationResult.Failure -> return AudioCommandResult.Rejected(
                AudioCommandRejection.INVALID_PROGRAM,
                result.diagnostics,
            )
            is AudioCompilationResult.Success -> result.program
        }
        return submit { it.playSfx(compiled, name) }
    }

    override fun setControl(name: AudioControlName, value: Float): AudioCommandResult {
        rejectedWhenUnavailable()?.let { return it }
        val declaration = when (val result = state.value.currentMusic?.control(name)) {
            null, is AudioLookupResult.Missing ->
                return AudioCommandResult.Rejected(AudioCommandRejection.UNKNOWN_CONTROL)
            is AudioLookupResult.Found -> result.value
        }
        if (!value.isFinite() || value !in declaration.range) {
            return AudioCommandResult.Rejected(AudioCommandRejection.CONTROL_OUT_OF_RANGE)
        }
        return submit { it.setControl(name, value) }
    }

    fun attachVisibilityJob(job: Job) {
        while (true) {
            val current = state.value
            if (current.closed) {
                job.cancel()
                return
            }
            if (state.compareAndSet(current, current.copy(visibilityJob = job))) return
        }
    }

    fun updateVisibility(value: MiniAppVisibility) {
        val updated = updateOpenState { current ->
            if (current.visibility == value) current else current.copy(visibility = value)
        } ?: return
        updateBackendPolicy(updated)
    }

    fun updateSettings(musicEnabled: Boolean, sfxEnabled: Boolean) {
        val updated = updateOpenState { current ->
            if (current.musicEnabled == musicEnabled && current.sfxEnabled == sfxEnabled) {
                current
            } else {
                current.copy(musicEnabled = musicEnabled, sfxEnabled = sfxEnabled)
            }
        } ?: return
        updateBackendPolicy(updated)
    }

    fun drainDiagnostics() {
        if (state.value.closed) return
        try {
            backend?.drainDiagnostics()?.let(diagnostics::report)
        } catch (error: Exception) {
            diagnostics.backendFailure(error)
        }
    }

    fun close() {
        val previous = closeState() ?: return
        previous.visibilityJob?.cancel()
        try {
            backend?.release()
        } catch (error: Exception) {
            diagnostics.backendFailure(error)
        } finally {
            onClosed()
        }
    }

    private fun rejectedWhenUnavailable(): AudioCommandResult.Rejected? = when {
        state.value.closed -> AudioCommandResult.Rejected(AudioCommandRejection.SESSION_CLOSED)
        backend == null -> AudioCommandResult.Rejected(AudioCommandRejection.BACKEND_UNAVAILABLE)
        else -> null
    }

    private fun updateBackendPolicy(current: State = state.value) {
        try {
            backend?.updatePolicy(policy(current))
        } catch (error: Exception) {
            diagnostics.backendFailure(error)
        }
    }

    private fun policy(current: State): AudioSessionPolicy {
        val visibilityPolicy = when (current.visibility) {
            MiniAppVisibility.ACTIVE -> AudioSessionPolicy.Active
            MiniAppVisibility.OBSCURED -> AudioSessionPolicy.Obscured
            MiniAppVisibility.BACKGROUND -> AudioSessionPolicy.Background
        }
        return visibilityPolicy.copy(
            musicGain = if (current.musicEnabled) visibilityPolicy.musicGain else 0f,
            acceptsNewSfx = current.sfxEnabled && visibilityPolicy.acceptsNewSfx,
        )
    }

    private fun updateState(transform: (State) -> State) {
        while (true) {
            val current = state.value
            if (state.compareAndSet(current, transform(current))) return
        }
    }

    private fun updateOpenState(transform: (State) -> State): State? {
        while (true) {
            val current = state.value
            if (current.closed) return null
            val updated = transform(current)
            if (updated == current) return null
            if (state.compareAndSet(current, updated)) return updated
        }
    }

    private fun closeState(): State? {
        while (true) {
            val current = state.value
            if (current.closed) return null
            if (state.compareAndSet(current, current.copy(closed = true, visibilityJob = null))) return current
        }
    }

    private inline fun submit(operation: (PlatformAudioSinkSession) -> AudioRuntimeSubmitResult): AudioCommandResult =
        try {
            when (operation(requireNotNull(backend))) {
                AudioRuntimeSubmitResult.Accepted,
                AudioRuntimeSubmitResult.AcceptedAfterEviction,
                AudioRuntimeSubmitResult.Coalesced,
                -> AudioCommandResult.Accepted
                AudioRuntimeSubmitResult.RejectedQueueFull ->
                    AudioCommandResult.Rejected(AudioCommandRejection.COMMAND_QUEUE_FULL)
                AudioRuntimeSubmitResult.RejectedDestroyed ->
                    AudioCommandResult.Rejected(AudioCommandRejection.SESSION_CLOSED)
            }
        } catch (error: Exception) {
            diagnostics.backendFailure(error)
            AudioCommandResult.Rejected(AudioCommandRejection.BACKEND_UNAVAILABLE)
        }

    private data class State(
        val visibility: MiniAppVisibility,
        val musicEnabled: Boolean,
        val sfxEnabled: Boolean,
        val currentMusic: AudioProgram? = null,
        val visibilityJob: Job? = null,
        val closed: Boolean = false,
    )
}
