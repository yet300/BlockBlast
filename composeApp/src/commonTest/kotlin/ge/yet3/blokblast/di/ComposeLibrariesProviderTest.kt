package ge.yet3.blokblast.di

import ge.yet.game.feature.settings.libraries.LibrariesSettingsComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComposeLibrariesProviderTest {

    @Test
    fun aboutlibraries_json_is_parsed_into_libraries() {
        val libraries = parseLibraries(
            json = """
                {
                  "libraries": [
                    {
                      "uniqueId": "test:library",
                      "artifactVersion": "1.0.0",
                      "name": "Test Library",
                      "description": "Test description",
                      "website": "https://example.com",
                      "licenses": []
                    }
                  ],
                  "licenses": {}
                }
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                LibrariesSettingsComponent.Library(
                    id = "test:library",
                    name = "Test Library",
                    description = "Test description",
                    url = "https://example.com",
                ),
            ),
            libraries,
        )
    }

    @Test
    fun description_prefers_metadata_then_licenses_then_version() {
        assertEquals(
            "Metadata description",
            libraryDescription(
                description = "Metadata description",
                licenseNames = listOf("MIT"),
                artifactVersion = "1.0",
            ),
        )
        assertEquals(
            "Apache-2.0, MIT",
            libraryDescription(
                description = " ",
                licenseNames = listOf("MIT", "Apache-2.0"),
                artifactVersion = "1.0",
            ),
        )
        assertEquals(
            "Version 1.0",
            libraryDescription(
                description = null,
                licenseNames = emptyList(),
                artifactVersion = "1.0",
            ),
        )
        assertEquals(
            "",
            libraryDescription(
                description = null,
                licenseNames = emptyList(),
                artifactVersion = null,
            ),
        )
    }

    @Test
    fun url_prefers_website_then_scm_and_can_be_absent() {
        assertEquals(
            "https://example.com",
            libraryUrl(
                website = "https://example.com",
                scmUrl = "https://github.com/example/repo",
            ),
        )
        assertEquals(
            "https://github.com/example/repo",
            libraryUrl(
                website = " ",
                scmUrl = "https://github.com/example/repo",
            ),
        )
        assertNull(libraryUrl(website = null, scmUrl = " "))
    }

    @Test
    fun libraries_are_sorted_case_insensitively() {
        val libraries = listOf(
            library(name = "zeta"),
            library(name = "Alpha"),
            library(name = "beta"),
        )

        assertEquals(
            listOf("Alpha", "beta", "zeta"),
            libraries.sortedForDisplay().map { it.name },
        )
    }

    private fun library(name: String) =
        LibrariesSettingsComponent.Library(
            id = name,
            name = name,
            description = "",
            url = null,
        )
}
