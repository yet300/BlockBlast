package ge.yet.blockblast.feature.settings

interface LibrariesSettingsComponent {

    val libraries: List<Library>

    fun onBackClicked()

    data class Library(
        val name: String,
        val description: String,
        val url: String,
    )
}
