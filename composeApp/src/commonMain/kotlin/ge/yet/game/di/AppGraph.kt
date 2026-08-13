package ge.yet.game.di

import ge.yet.game.feature.root.RootComponent

/**
 * Common entry point for the dependency graph.
 *
 * This interface defines the public API of the DI graph that is accessible
 * from common code. The final implementation is generated in platform-specific
 * code (like [AndroidAppGraph]).
 */
interface AppGraph {
    val rootFactory: RootComponent.Factory
}
