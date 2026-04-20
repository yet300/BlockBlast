package ge.yet.blockblast.feature.settings.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import ge.yet.blockblast.feature.settings.DefaultSettingsComponentFactory
import ge.yet.blockblast.feature.settings.SettingsComponent

@ContributesTo(AppScope::class)
@BindingContainer
object SettingsBindings {
    @Provides
    internal fun provideSettingsComponentFactory(
        impl: DefaultSettingsComponentFactory,
    ): SettingsComponent.Factory = impl
}
