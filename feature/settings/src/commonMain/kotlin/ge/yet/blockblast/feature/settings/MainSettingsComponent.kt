package ge.yet.blockblast.feature.settings

import com.arkivanov.decompose.value.Value

interface MainSettingsComponent {

    val model: Value<Model>

    fun onSoundToggled(enabled: Boolean)
    fun onVibrationToggled(enabled: Boolean)
    fun onDarkThemeToggled(enabled: Boolean)
    fun onMoreClicked()
    fun onBackClicked()

    data class Model(
        val soundEnabled: Boolean,
        val vibrationEnabled: Boolean,
        val darkTheme: Boolean,
    )
}
