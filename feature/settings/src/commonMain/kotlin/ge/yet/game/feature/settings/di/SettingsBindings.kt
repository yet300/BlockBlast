package ge.yet.game.feature.settings.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import ge.yet.game.feature.settings.DefaultSettingsComponentFactory
import ge.yet.game.feature.settings.SettingsComponent

@ContributesTo(AppScope::class)
@BindingContainer
abstract class SettingsBindings {
    @Binds
    internal abstract val DefaultSettingsComponentFactory.bindSettingsComponentFactory: SettingsComponent.Factory
}
