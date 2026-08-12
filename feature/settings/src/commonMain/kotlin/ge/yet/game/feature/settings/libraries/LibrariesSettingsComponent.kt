package ge.yet.game.feature.settings.libraries

import com.arkivanov.decompose.value.Value

interface LibrariesSettingsComponent {

    val model: Value<Model>

    fun onBackClicked()

    data class Model(
        val libraries: List<Library> = emptyList(),
    )

    data class Library(
        val id: String,
        val name: String,
        val description: String,
        val url: String?,
    )
}
