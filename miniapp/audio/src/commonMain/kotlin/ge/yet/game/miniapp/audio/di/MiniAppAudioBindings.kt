package ge.yet.game.miniapp.audio.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphPrivate
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.game.domain.repository.CrashlyticsRepository
import ge.yet.game.domain.repository.SettingsRepository
import ge.yet.game.miniapp.audio.MiniAppAudioEngine
import ge.yet.game.miniapp.audio.internal.DefaultMiniAppAudioEngine
import ge.yet.game.miniapp.audio.internal.PlatformAudioSink
import ge.yet.game.miniapp.audio.internal.UnavailablePlatformAudioSink
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesTo(AppScope::class)
@BindingContainer
object MiniAppAudioBindings {
    @Provides
    @SingleIn(AppScope::class)
    internal fun provideMiniAppAudioEngine(
        appScope: CoroutineScope,
        settings: SettingsRepository,
        crashlytics: CrashlyticsRepository,
        sink: PlatformAudioSink,
    ): MiniAppAudioEngine = DefaultMiniAppAudioEngine(appScope, settings, sink, crashlytics).also { engine ->
        appScope.launch(CoroutineName("miniapp-audio-settings")) { engine.observeSettings() }
        appScope.launch(CoroutineName("miniapp-audio-diagnostics")) { engine.reportDiagnostics() }
    }
}

/** Temporary fallback replaced independently by each platform sink binding. */
@ContributesTo(AppScope::class)
@BindingContainer
object UnavailableMiniAppAudioSinkBindings {
    @Provides
    @SingleIn(AppScope::class)
    @GraphPrivate
    internal fun providePlatformAudioSink(): PlatformAudioSink = UnavailablePlatformAudioSink
}
