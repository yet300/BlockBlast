package ge.yet.blokblast.data.di

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
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
@ContributesTo(AppScope::class)
@BindingContainer
abstract class AndroidDataBindings {

    @Binds
    internal abstract val AndroidPlatformSoundPlayer.bindPlatformSoundPlayer: PlatformSoundPlayer

    @Binds
    internal abstract val AndroidPlatformVibrator.bindPlatformVibrator: PlatformVibrator

    @Binds
    internal abstract val AndroidStoreReviewRepository.bindStoreReviewRepository: StoreReviewRepository
}
