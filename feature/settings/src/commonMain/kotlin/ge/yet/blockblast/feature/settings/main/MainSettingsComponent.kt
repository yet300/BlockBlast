package ge.yet.blockblast.feature.settings.main

import com.arkivanov.decompose.value.Value

interface MainSettingsComponent {

    val model: Value<Model>

    fun onMusicToggled(enabled: Boolean)
    fun onSfxToggled(enabled: Boolean)
    fun onVibrationToggled(enabled: Boolean)
    fun onDarkThemeToggled(enabled: Boolean)
    fun onMoreClicked()
    fun onBackClicked()

    data class Model(
        val musicEnabled: Boolean,
        val sfxEnabled: Boolean,
        val vibrationEnabled: Boolean,
        val darkTheme: Boolean,
    )
}
