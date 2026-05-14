package ge.yet.blockblast.feature.settings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import dev.zacsweers.metro.Inject
import ge.yet.blockblast.feature.settings.libraries.DefaultLibrariesSettingsComponent
import ge.yet.blockblast.feature.settings.main.DefaultMainSettingsComponent
import ge.yet.blockblast.feature.settings.main.store.SettingsStoreFactory
import ge.yet.blockblast.feature.settings.more.DefaultMoreSettingsComponent
import ge.yet.blokblast.domain.repository.AnalyticRepository
import kotlinx.serialization.Serializable

internal class DefaultSettingsComponent(
    componentContext: ComponentContext,
    storeFactory: SettingsStoreFactory,
    private val analytics: AnalyticRepository,
    private val onBackClickedCb: () -> Unit,
) : SettingsComponent, ComponentContext by componentContext {

    private val store = instanceKeeper.getStore { storeFactory.create() }
    private val navigation = StackNavigation<Config>()

    override val stack: Value<ChildStack<*, SettingsComponent.Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Main,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    override fun onBackClicked() {
        if (stack.value.backStack.isEmpty()) {
            analytics.logEvent(eventName = "settings_back_clicked", params = null)
            onBackClickedCb()
        } else {
            navigation.pop()
        }
    }

    private fun createChild(
        config: Config,
        componentContext: ComponentContext,
    ): SettingsComponent.Child = when (config) {
        Config.Main -> SettingsComponent.Child.Main(
            DefaultMainSettingsComponent(
                componentContext = componentContext,
                store = store,
                onMoreClickedCb = ::pushMore,
                onBackClickedCb = ::onBackClicked,
            )
        )

        Config.More -> SettingsComponent.Child.More(
            DefaultMoreSettingsComponent(
                componentContext = componentContext,
                onLibrariesClickedCb = ::pushLibraries,
                onBackClickedCb = ::onBackClicked,
            )
        )

        Config.Libraries -> SettingsComponent.Child.Libraries(
            DefaultLibrariesSettingsComponent(
                componentContext = componentContext,
                onBackClickedCb = ::onBackClicked,
            )
        )
    }

    private fun pushMore() {
        analytics.logEvent(eventName = "settings_more_clicked", params = null)
        navigation.push(Config.More)
    }

    private fun pushLibraries() {
        analytics.logEvent(eventName = "settings_libraries_clicked", params = null)
        navigation.push(Config.Libraries)
    }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Main : Config

        @Serializable
        data object More : Config

        @Serializable
        data object Libraries : Config
    }
}

@Inject
internal class DefaultSettingsComponentFactory(
    private val storeFactory: SettingsStoreFactory,
    private val analytics: AnalyticRepository,
) : SettingsComponent.Factory {
    override fun create(
        componentContext: ComponentContext,
        onBackClicked: () -> Unit,
    ): SettingsComponent = DefaultSettingsComponent(
        componentContext = componentContext,
        storeFactory = storeFactory,
        analytics = analytics,
        onBackClickedCb = onBackClicked,
    )
}
