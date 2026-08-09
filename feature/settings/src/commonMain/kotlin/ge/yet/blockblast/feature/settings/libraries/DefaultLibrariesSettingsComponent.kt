package ge.yet.blockblast.feature.settings.libraries

import com.app.common.decompose.componentCoroutineScope
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class DefaultLibrariesSettingsComponent(
    componentContext: ComponentContext,
    private val librariesProvider: LibrariesProvider,
    private val onBackClickedCb: () -> Unit,
    coroutineScope: CoroutineScope = componentContext.componentCoroutineScope(),
) : LibrariesSettingsComponent, ComponentContext by componentContext {

    private val modelState = MutableValue(LibrariesSettingsComponent.Model())
    override val model: Value<LibrariesSettingsComponent.Model> = modelState

    init {
        coroutineScope.launch {
            val libraries = try {
                librariesProvider.loadLibraries()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                emptyList()
            }
            modelState.value = LibrariesSettingsComponent.Model(libraries = libraries)
        }
    }

    override fun onBackClicked() = onBackClickedCb()
}
