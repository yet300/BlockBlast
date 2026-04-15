package ge.yet.blokblast.data.di

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.observable.makeObservable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.data.repository.DefaultAudioRepository
import ge.yet.blokblast.data.repository.DefaultVibrationRepository
import ge.yet.blokblast.data.repository.LocalGameSaveRepository
import ge.yet.blokblast.data.repository.SettingsBackedSettingsRepository
import ge.yet.blokblast.domain.repository.AudioRepository
import ge.yet.blokblast.domain.repository.GameSaveRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import ge.yet.blokblast.domain.repository.VibrationRepository

/**
 * Data-layer bindings contributed to the app-wide [AppScope] graph.
 *
 * Every binding here is `internal`: the concrete implementation classes never
 * leak out to composeApp / feature modules — only the domain interfaces do.
 *
 * Platform-specific bindings ([ge.yet.blokblast.data.platform.PlatformSoundPlayer],
 * [ge.yet.blokblast.data.platform.PlatformVibrator], concrete [ObservableSettings])
 * are contributed by sibling `androidMain` / `nativeMain` binding containers.
 */
@OptIn(ExperimentalSettingsApi::class)
@ContributesTo(AppScope::class)
@BindingContainer
object DataBindings {

    @Provides
    @SingleIn(AppScope::class)
    internal fun provideGameSaveRepository(
        impl: LocalGameSaveRepository,
    ): GameSaveRepository = impl

    @Provides
    @SingleIn(AppScope::class)
    internal fun provideSettingsRepository(
        impl: SettingsBackedSettingsRepository,
    ): SettingsRepository = impl

    @Provides
    @SingleIn(AppScope::class)
    internal fun provideAudioRepository(
        impl: DefaultAudioRepository,
    ): AudioRepository = impl

    @Provides
    @SingleIn(AppScope::class)
    internal fun provideVibrationRepository(
        impl: DefaultVibrationRepository,
    ): VibrationRepository = impl

    /**
     * Widening binding so consumers that only need the base [Settings] API share
     * the same singleton instance as [SettingsBackedSettingsRepository] — no
     * duplicate stores, no lost writes.
     */
    @Provides
    internal fun provideSettings(): Settings = Settings()

    @Provides
    @SingleIn(AppScope::class)
    internal fun provideObservableSettings(impl: Settings): ObservableSettings = impl.makeObservable()
}
