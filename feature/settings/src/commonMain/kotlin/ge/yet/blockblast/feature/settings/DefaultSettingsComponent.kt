package ge.yet.blockblast.feature.settings

import com.app.common.decompose.asValue
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import dev.zacsweers.metro.Inject
import ge.yet.blockblast.feature.settings.integration.stateToModel
import ge.yet.blockblast.feature.settings.store.SettingsStore
import ge.yet.blockblast.feature.settings.store.SettingsStoreFactory
import ge.yet.blokblast.domain.repository.AnalyticRepository

internal class DefaultSettingsComponent(
    componentContext: ComponentContext,
    private val storeFactory: SettingsStoreFactory,
    private val analytics: AnalyticRepository,
    private val onBackClickedCb: () -> Unit,
) : SettingsComponent, ComponentContext by componentContext {


    private val store = instanceKeeper.getStore { storeFactory.create() }

    override val model: Value<SettingsComponent.Model> = store.asValue().map(stateToModel)

    override fun onSoundToggled(enabled: Boolean) {
        store.accept(SettingsStore.Intent.SetSound(enabled))
    }

    override fun onVibrationToggled(enabled: Boolean) {
        store.accept(SettingsStore.Intent.SetVibration(enabled))
    }

    override fun onDarkThemeToggled(enabled: Boolean) {
        store.accept(SettingsStore.Intent.SetDark(enabled))
    }

    override fun onBackClicked() {
        analytics.logEvent(
            eventName = "settings_back_clicked",
            params = null,
        )
        onBackClickedCb()
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
