package ge.yet.blokblast.data.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
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
@ContributesTo(AppScope::class)
@BindingContainer
abstract class NativeDataBindings {

    @Binds
    internal abstract val NativePlatformSoundPlayer.bindPlatformSoundPlayer: PlatformSoundPlayer

    @Binds
    internal abstract val NativePlatformVibrator.bindPlatformVibrator: PlatformVibrator

    @Binds
    internal abstract val IosStoreReviewRepository.bindStoreReviewRepository: StoreReviewRepository
}
