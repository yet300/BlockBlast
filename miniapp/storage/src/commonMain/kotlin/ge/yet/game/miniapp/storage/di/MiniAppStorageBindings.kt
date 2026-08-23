package ge.yet.game.miniapp.storage.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import ge.yet.game.miniapp.api.MiniAppAdditionalDataCleaner
import ge.yet.game.miniapp.api.MiniAppDataResetter
import ge.yet.game.miniapp.api.MiniAppLegacyStorageKeys
import ge.yet.game.miniapp.api.MiniAppStorageProvider
import ge.yet.game.miniapp.storage.DefaultMiniAppDataResetter
import ge.yet.game.miniapp.storage.DefaultMiniAppStorageProvider

@ContributesTo(AppScope::class)
@BindingContainer
abstract class MiniAppStorageBindings {
    @get:Multibinds(allowEmpty = true)
    abstract val legacyStorageKeys: Set<MiniAppLegacyStorageKeys>

    @get:Multibinds(allowEmpty = true)
    abstract val additionalDataCleaners: Set<MiniAppAdditionalDataCleaner>

    @get:Binds
    internal abstract val DefaultMiniAppStorageProvider.bindStorageProvider: MiniAppStorageProvider

    @get:Binds
    internal abstract val DefaultMiniAppDataResetter.bindDataResetter: MiniAppDataResetter
}
