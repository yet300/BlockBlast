package ge.yet3.blokblast.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import ge.yet.blokblast.domain.repository.AudioFileProvider

@ContributesTo(AppScope::class)
@BindingContainer
abstract class ComposeAppBindings {

    @Binds
    internal abstract val ComposeAudioFileProvider.bindAudioFileProvider: AudioFileProvider
}
