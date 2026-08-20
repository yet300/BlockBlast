package ge.yet.game.miniapp.integration

import ge.yet.game.miniapp.compose.MiniAppRegistry

interface CounterIntegrationGraph {
    val registry: MiniAppRegistry
}

expect fun createCounterIntegrationGraph(): CounterIntegrationGraph
