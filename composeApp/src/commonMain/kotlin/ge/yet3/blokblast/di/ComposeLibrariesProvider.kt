package ge.yet3.blokblast.di

import blockblast.composeapp.generated.resources.Res
import com.app.common.AppDispatchers
import com.mikepenz.aboutlibraries.Libs
import dev.zacsweers.metro.Inject
import ge.yet.blockblast.feature.settings.libraries.LibrariesProvider
import ge.yet.blockblast.feature.settings.libraries.LibrariesSettingsComponent
import kotlinx.coroutines.withContext

private const val ABOUT_LIBRARIES_RESOURCE = "files/aboutlibraries.json"

@Inject
internal class ComposeLibrariesProvider(
    private val dispatchers: AppDispatchers,
) : LibrariesProvider {

    override suspend fun loadLibraries(): List<LibrariesSettingsComponent.Library> =
        withContext(dispatchers.default) {
            parseLibraries(
                json = Res.readBytes(ABOUT_LIBRARIES_RESOURCE).decodeToString(),
            )
        }
}

internal fun parseLibraries(json: String): List<LibrariesSettingsComponent.Library> =
    Libs.Builder()
        .withJson(json)
        .build()
        .libraries
        .map { library ->
            LibrariesSettingsComponent.Library(
                id = library.uniqueId,
                name = library.name,
                description = libraryDescription(
                    description = library.description,
                    licenseNames = library.licenses.map { it.name },
                    artifactVersion = library.artifactVersion,
                ),
                url = libraryUrl(
                    website = library.website,
                    scmUrl = library.scm?.url,
                ),
            )
        }
        .sortedForDisplay()

internal fun libraryDescription(
    description: String?,
    licenseNames: List<String>,
    artifactVersion: String?,
): String {
    description?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

    val licenses = licenseNames
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .sorted()
    if (licenses.isNotEmpty()) return licenses.joinToString()

    return artifactVersion
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { "Version $it" }
        .orEmpty()
}

internal fun libraryUrl(
    website: String?,
    scmUrl: String?,
): String? =
    sequenceOf(website, scmUrl)
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull()

internal fun List<LibrariesSettingsComponent.Library>.sortedForDisplay():
    List<LibrariesSettingsComponent.Library> =
    sortedWith(
        compareBy<LibrariesSettingsComponent.Library> { it.name.lowercase() }
            .thenBy { it.name }
            .thenBy { it.id },
    )
