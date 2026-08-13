package ge.yet.game.miniapp.metro

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import ge.yet.game.miniapp.compose.MiniAppPlugin
import ge.yet.game.miniapp.compose.MiniAppRegistry

@ContributesTo(AppScope::class)
@BindingContainer
abstract class MiniAppMetroBindings {
    @get:Multibinds(allowEmpty = true)
    abstract val plugins: Set<MiniAppPlugin>

    @get:Multibinds(allowEmpty = true)
    abstract val expectations: Set<MiniAppRegistryExpectation>

    @get:Binds
    abstract val DefaultMiniAppRegistry.bindRegistry: MiniAppRegistry
}
