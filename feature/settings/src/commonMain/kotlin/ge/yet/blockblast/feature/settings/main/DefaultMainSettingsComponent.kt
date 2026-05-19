package ge.yet.blockblast.feature.settings.main

import com.app.common.decompose.asValue
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import ge.yet.blockblast.feature.settings.main.integration.stateToModel
import ge.yet.blockblast.feature.settings.main.store.SettingsStore

internal class DefaultMainSettingsComponent(
    componentContext: ComponentContext,
    private val store: SettingsStore,
    private val onMoreClickedCb: () -> Unit,
    private val onBackClickedCb: () -> Unit,
) : MainSettingsComponent, ComponentContext by componentContext {

    override val model: Value<MainSettingsComponent.Model> =
        store.asValue().map(stateToModel)

    override fun onMusicToggled(enabled: Boolean) {
        store.accept(SettingsStore.Intent.SetMusic(enabled))
    }

    override fun onSfxToggled(enabled: Boolean) {
        store.accept(SettingsStore.Intent.SetSfx(enabled))
    }

    override fun onVibrationToggled(enabled: Boolean) {
        store.accept(SettingsStore.Intent.SetVibration(enabled))
    }

    override fun onDarkThemeToggled(enabled: Boolean) {
        store.accept(SettingsStore.Intent.SetDark(enabled))
    }

    override fun onMoreClicked() = onMoreClickedCb()

    override fun onBackClicked() = onBackClickedCb()
}
