package ge.yet.blokblast.data.di

import com.russhwolf.settings.ExperimentalSettingsApi
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.data.platform.NativePlatformSoundPlayer
import ge.yet.blokblast.data.platform.NativePlatformVibrator
import ge.yet.blokblast.data.platform.PlatformSoundPlayer
import ge.yet.blokblast.data.platform.PlatformVibrator
import ge.yet.blokblast.data.repository.IosStoreReviewRepository
import ge.yet.blokblast.domain.repository.StoreReviewRepository
import platform.Foundation.NSUserDefaults

/**
 * iOS bindings for the data layer. Uses [NSUserDefaults] as the backing store
 * for user preferences — matches the native feel on iOS and is immediately
 * observable through multiplatform-settings.
 */
@OptIn(ExperimentalSettingsApi::class)
@ContributesTo(AppScope::class)
@BindingContainer
object NativeDataBindings {

    @Provides
    @SingleIn(AppScope::class)
    internal fun providePlatformSoundPlayer(
        impl: NativePlatformSoundPlayer,
    ): PlatformSoundPlayer = impl

    @Provides
    @SingleIn(AppScope::class)
    internal fun providePlatformVibrator(
        impl: NativePlatformVibrator,
    ): PlatformVibrator = impl

    @Provides
    @SingleIn(AppScope::class)
    internal fun provideStoreReviewRepository(
        impl: IosStoreReviewRepository,
    ): StoreReviewRepository = impl
}
