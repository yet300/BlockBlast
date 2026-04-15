package ge.yet.blockblast.feature.home

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value

/**
 * Home / main-menu screen. Exposes best score and navigation to Game/Settings.
 */
interface HomeComponent {

    val model: Value<Model>

    fun onPlayClicked()

    data class Model(
        val bestScore: Long,
        val hasSavedGame: Boolean,
    )

    fun interface Factory {
        fun create(
            componentContext: ComponentContext,
            onPlayClicked: () -> Unit,
        ): HomeComponent
    }
}
