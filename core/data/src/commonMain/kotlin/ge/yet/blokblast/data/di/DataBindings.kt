package ge.yet.blokblast.data.di

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.observable.makeObservable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.data.repository.AnalyticRepositoryImpl
import ge.yet.blokblast.data.repository.CrashlyticsRepositoryImpl
import ge.yet.blokblast.data.repository.DefaultAudioRepository
import ge.yet.blokblast.data.repository.DefaultVibrationRepository
import ge.yet.blokblast.data.repository.SettingsBackedGameSaveRepository
import ge.yet.blokblast.data.repository.SettingsBackedSettingsRepository
import ge.yet.blokblast.domain.repository.AnalyticRepository
import ge.yet.blokblast.domain.repository.AudioRepository
import ge.yet.blokblast.domain.repository.CrashlyticsRepository
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
abstract class DataBindings {

    @Binds
    internal abstract val SettingsBackedGameSaveRepository.bindGameSaveRepository: GameSaveRepository

    @Binds
    internal abstract val SettingsBackedSettingsRepository.bindSettingsRepository: SettingsRepository

    @Binds
    internal abstract val DefaultAudioRepository.bindAudioRepository: AudioRepository

    @Binds
    internal abstract val DefaultVibrationRepository.bindVibrationRepository: VibrationRepository

    @Binds
    internal abstract val CrashlyticsRepositoryImpl.bindCrashlyticsRepository: CrashlyticsRepository

    @Binds
    internal abstract val AnalyticRepositoryImpl.bindAnalyticRepository: AnalyticRepository

    /**
     * Widening binding so consumers that only need the base [Settings] API share
     * the same singleton instance as [SettingsBackedSettingsRepository] — no
     * duplicate stores, no lost writes.
     */
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        internal fun provideSettings(): Settings = Settings()

        @Provides
        @SingleIn(AppScope::class)
        internal fun provideObservableSettings(impl: Settings): ObservableSettings = impl.makeObservable()
    }
}
