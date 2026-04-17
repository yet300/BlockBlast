package ge.yet3.blokblast.di

import android.content.Context
import com.app.common.di.CommonBindings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import ge.yet.blockblast.feature.game.di.GameBindings
import ge.yet.blockblast.feature.home.di.HomeBindings
import ge.yet.blockblast.feature.root.RootComponent
import ge.yet.blockblast.feature.root.di.RootBindings
import ge.yet.blockblast.feature.settings.di.SettingsBindings
import ge.yet.blokblast.data.di.AndroidDataBindings
import ge.yet.blokblast.data.di.DataBindings
import ge.yet.blokblast.domain.di.DomainBindings

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [
        CommonBindings::class,
        DomainBindings::class,
        DataBindings::class,
        AndroidDataBindings::class,
        ComposeAppBindings::class,
        RootBindings::class,
        HomeBindings::class,
        GameBindings::class,
        SettingsBindings::class,
    ],
)
interface AndroidAppGraph : AppGraph {

    override val rootFactory: RootComponent.Factory

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides context: Context,
        ): AndroidAppGraph
    }
}
