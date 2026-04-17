package ge.yet3.blokblast.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.domain.repository.AudioFileProvider

@ContributesTo(AppScope::class)
@BindingContainer
object ComposeAppBindings {

    @Provides
    @SingleIn(AppScope::class)
    internal fun provideAudioFileProvider(
        impl: ComposeAudioFileProvider,
    ): AudioFileProvider = impl
}
