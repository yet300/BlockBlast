package ge.yet.game.miniapp.audio.internal

import ge.yet.game.domain.repository.CrashlyticsRepository

internal class AudioDiagnostics(
    private val crashlytics: CrashlyticsRepository,
) {
    fun backendFailure(error: Throwable) = safely { crashlytics.logException(error) }

    fun report(snapshot: AudioRuntimeDiagnosticsSnapshot) {
        if (snapshot.validationRejections > 0) report("validation_rejections", snapshot.validationRejections)
        if (snapshot.queueOverflows > 0) report("queue_overflows", snapshot.queueOverflows)
        if (snapshot.forcedVoiceShedding > 0) report("forced_voice_shedding", snapshot.forcedVoiceShedding)
        if (snapshot.callbackFailures > 0) report("backend_failures", snapshot.callbackFailures)
        if (snapshot.underruns >= REPEATED_UNDERRUN_THRESHOLD) report("underruns", snapshot.underruns)
    }

    private fun report(name: String, count: Long) = safely {
        crashlytics.logMessage("miniapp_audio_$name count=$count")
    }

    private inline fun safely(operation: () -> Unit) {
        try {
            operation()
        } catch (_: Exception) {
            // Diagnostics are best-effort and never alter session behavior.
        }
    }
}

private const val REPEATED_UNDERRUN_THRESHOLD = 3L
