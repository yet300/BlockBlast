package ge.yet3.blokblast.di

import android.content.Context
import com.app.common.di.CommonBindings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import ge.yet.blockblast.feature.game.di.GameBindings
import ge.yet.game.feature.home.di.HomeBindings
import ge.yet.game.feature.root.RootComponent
import ge.yet.game.feature.root.di.RootBindings
import ge.yet.game.feature.settings.di.SettingsBindings
import ge.yet.game.data.di.AndroidDataBindings
import ge.yet.game.data.di.DataBindings
import ge.yet.game.telemetry.di.TelemetryBindings

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [
        CommonBindings::class,
        DataBindings::class,
        AndroidDataBindings::class,
        TelemetryBindings::class,
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
