package ge.yet.blockblast.feature.settings.more

import com.arkivanov.decompose.ComponentContext

internal class DefaultMoreSettingsComponent(
    componentContext: ComponentContext,
    private val onLibrariesClickedCb: () -> Unit,
    private val onBackClickedCb: () -> Unit,
) : MoreSettingsComponent, ComponentContext by componentContext {

    override fun onLibrariesClicked() = onLibrariesClickedCb()
    override fun onBackClicked() = onBackClickedCb()
}
