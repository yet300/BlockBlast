package ge.yet.game.feature.settings.libraries

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import ge.yet.game.feature.settings.libraries.DefaultLibrariesSettingsComponent
import ge.yet.game.feature.settings.libraries.LibrariesProvider
import ge.yet.game.feature.settings.libraries.LibrariesSettingsComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLibrariesSettingsComponentTest {

    @Test
    fun provider_libraries_are_published_after_loading() = runTest {
        val expected = listOf(
            LibrariesSettingsComponent.Library(
                id = "com.mikepenz:aboutlibraries-core",
                name = "AboutLibraries",
                description = "Dependency and license metadata",
                url = "https://github.com/mikepenz/AboutLibraries",
            ),
        )
        val component = component(
            provider = LibrariesProvider { expected },
            coroutineScope = this,
        )

        assertEquals(emptyList(), component.model.value.libraries)

        runCurrent()

        assertEquals(expected, component.model.value.libraries)
    }

    @Test
    fun provider_failure_keeps_an_empty_model() = runTest {
        val component = component(
            provider = LibrariesProvider { error("Broken resource") },
            coroutineScope = this,
        )

        runCurrent()

        assertEquals(emptyList(), component.model.value.libraries)
    }

    @Test
    fun back_click_invokes_parent_callback() = runTest {
        var backClicks = 0
        val component = component(
            provider = LibrariesProvider { emptyList() },
            coroutineScope = this,
            onBackClicked = { backClicks++ },
        )

        component.onBackClicked()

        assertEquals(1, backClicks)
    }

    private fun component(
        provider: LibrariesProvider,
        coroutineScope: CoroutineScope,
        onBackClicked: () -> Unit = {},
    ): DefaultLibrariesSettingsComponent =
        DefaultLibrariesSettingsComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            librariesProvider = provider,
            coroutineScope = coroutineScope,
            onBackClickedCb = onBackClicked,
        )
}
