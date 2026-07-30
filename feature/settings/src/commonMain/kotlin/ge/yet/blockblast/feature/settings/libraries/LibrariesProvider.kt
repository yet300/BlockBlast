package ge.yet.blockblast.feature.settings.libraries

fun interface LibrariesProvider {
    suspend fun loadLibraries(): List<LibrariesSettingsComponent.Library>
}
