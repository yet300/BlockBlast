package ge.yet3.blokblast.di

import com.app.common.di.CommonBindings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraphFactory
import ge.yet.blockblast.feature.game.di.GameBindings
import ge.yet.blockblast.feature.home.di.HomeBindings
import ge.yet.blockblast.feature.root.RootComponent
import ge.yet.blockblast.feature.root.di.RootBindings
import ge.yet.blockblast.feature.settings.di.SettingsBindings
import ge.yet.blokblast.data.di.DataBindings
import ge.yet.blokblast.data.di.NativeDataBindings
import ge.yet.blokblast.domain.di.DomainBindings


@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [
        CommonBindings::class,
        DomainBindings::class,
        DataBindings::class,
        NativeDataBindings::class,
        ComposeAppBindings::class,
        RootBindings::class,
        HomeBindings::class,
        GameBindings::class,
        SettingsBindings::class,
    ],
)
interface NativeAppGraph : AppGraph {

    override val rootFactory: RootComponent.Factory

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(): NativeAppGraph
    }
}

fun getNativeAppGraph(): NativeAppGraph {
    return createGraphFactory<NativeAppGraph.Factory>().create()
}