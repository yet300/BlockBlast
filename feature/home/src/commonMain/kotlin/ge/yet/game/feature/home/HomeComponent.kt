package ge.yet.game.feature.home

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value

/**
 * Home / main-menu screen. Exposes saved-game status and game navigation.
 */
interface HomeComponent {

    val model: Value<Model>

    fun onContinueClicked()
    fun onNewGameClicked()

    data class Model(
        val hasSavedGame: Boolean,
    )

    fun interface Factory {
        fun create(
            componentContext: ComponentContext,
            onContinueClicked: (Boolean) -> Unit,
            onNewGameClicked: (Boolean) -> Unit,
        ): HomeComponent
    }
}
