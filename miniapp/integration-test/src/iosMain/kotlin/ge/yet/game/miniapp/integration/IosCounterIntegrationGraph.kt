package ge.yet.game.miniapp.integration

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import ge.yet.game.miniapp.metro.MiniAppMetroBindings

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [MiniAppMetroBindings::class],
)
internal interface IosCounterIntegrationGraph : CounterIntegrationGraph

actual fun createCounterIntegrationGraph(): CounterIntegrationGraph =
    createGraph<IosCounterIntegrationGraph>()
