package ge.yet.blokblast.data.di

import android.content.Context
import com.russhwolf.settings.ExperimentalSettingsApi
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.data.platform.AndroidPlatformSoundPlayer
import ge.yet.blokblast.data.platform.AndroidPlatformVibrator
import ge.yet.blokblast.data.platform.PlatformSoundPlayer
import ge.yet.blokblast.data.platform.PlatformVibrator
import ge.yet.blokblast.data.repository.AndroidStoreReviewRepository
import ge.yet.blokblast.domain.repository.StoreReviewRepository

/**
 * Android bindings for the data layer. [Context] is supplied to the graph
 * by the host via `@BindsInstance` on the top-level graph factory — that's the
 * only place Android needs to reach through the DI boundary.
 */
@OptIn(ExperimentalSettingsApi::class)
@ContributesTo(AppScope::class)
@BindingContainer
object AndroidDataBindings {

    @Provides
    @SingleIn(AppScope::class)
    internal fun providePlatformSoundPlayer(
        impl: AndroidPlatformSoundPlayer,
    ): PlatformSoundPlayer = impl

    @Provides
    @SingleIn(AppScope::class)
    internal fun providePlatformVibrator(
        impl: AndroidPlatformVibrator,
    ): PlatformVibrator = impl

    @Provides
    @SingleIn(AppScope::class)
    internal fun provideStoreReviewRepository(
        impl: AndroidStoreReviewRepository,
    ): StoreReviewRepository = impl
}
