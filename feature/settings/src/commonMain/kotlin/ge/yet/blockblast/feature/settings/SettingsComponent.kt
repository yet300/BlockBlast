package ge.yet.blockblast.feature.settings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value

/**
 * Settings screen. Reachable from BOTH Home and Game via Root navigation.
 */
interface SettingsComponent {

    val model: Value<Model>

    fun onSoundToggled(enabled: Boolean)
    fun onVibrationToggled(enabled: Boolean)
    fun onDarkThemeToggled(enabled: Boolean)
    fun onBackClicked()

    data class Model(
        val soundEnabled: Boolean,
        val vibrationEnabled: Boolean,
        val darkTheme: Boolean,
    )

    fun interface Factory {
        fun create(
            componentContext: ComponentContext,
            onBackClicked: () -> Unit,
        ): SettingsComponent
    }
}
