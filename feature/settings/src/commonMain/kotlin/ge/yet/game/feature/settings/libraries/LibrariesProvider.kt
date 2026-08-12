package ge.yet.game.feature.settings.libraries

fun interface LibrariesProvider {
    suspend fun loadLibraries(): List<LibrariesSettingsComponent.Library>
}
