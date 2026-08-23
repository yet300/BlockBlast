package ge.yet.game.feature.settings.more

import com.arkivanov.decompose.value.Value

interface MoreSettingsComponent {

    val model: Value<Model>

    fun onAdsToggled(enabled: Boolean)
    fun onSupportClicked()
    fun onLibrariesClicked()
    fun onResetGameDataClicked()
    fun onBackClicked()

    data class Model(
        val adsEnabled: Boolean,
    )
}
