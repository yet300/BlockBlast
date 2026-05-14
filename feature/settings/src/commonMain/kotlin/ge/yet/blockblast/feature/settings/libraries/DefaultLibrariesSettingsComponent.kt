package ge.yet.blockblast.feature.settings.libraries

import com.arkivanov.decompose.ComponentContext

internal class DefaultLibrariesSettingsComponent(
    componentContext: ComponentContext,
    private val onBackClickedCb: () -> Unit,
) : LibrariesSettingsComponent, ComponentContext by componentContext {

    override val libraries: List<LibrariesSettingsComponent.Library> = DEFAULT_LIBRARIES

    override fun onBackClicked() = onBackClickedCb()
}
