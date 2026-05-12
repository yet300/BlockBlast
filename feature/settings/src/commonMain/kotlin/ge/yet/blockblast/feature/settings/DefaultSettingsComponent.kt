package ge.yet.blockblast.feature.settings

import com.app.common.decompose.asValue
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import dev.zacsweers.metro.Inject
import ge.yet.blockblast.feature.settings.integration.stateToModel
import ge.yet.blockblast.feature.settings.store.SettingsStore
import ge.yet.blockblast.feature.settings.store.SettingsStoreFactory
import ge.yet.blokblast.domain.repository.AnalyticRepository
import kotlinx.serialization.Serializable

internal class DefaultSettingsComponent(
    componentContext: ComponentContext,
    storeFactory: SettingsStoreFactory,
    private val analytics: AnalyticRepository,
    private val onBackClickedCb: () -> Unit,
) : SettingsComponent, ComponentContext by componentContext {

    private val store = instanceKeeper.getStore { storeFactory.create() }
    private val navigation = StackNavigation<Config>()

    override val stack: Value<ChildStack<*, SettingsComponent.Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Main,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    override fun onBackClicked() {
        if (stack.value.backStack.isEmpty()) {
            analytics.logEvent(eventName = "settings_back_clicked", params = null)
            onBackClickedCb()
        } else {
            navigation.pop()
        }
    }

    private fun createChild(
        config: Config,
        componentContext: ComponentContext,
    ): SettingsComponent.Child = when (config) {
        Config.Main -> SettingsComponent.Child.Main(
            MainSettingsComponentImpl(
                componentContext = componentContext,
                store = store,
                onMoreClicked = {
                    analytics.logEvent(eventName = "settings_more_clicked", params = null)
                    navigation.push(Config.More)
                },
                onBackClicked = ::onBackClicked,
            )
        )

        Config.More -> SettingsComponent.Child.More(
            MoreSettingsComponentImpl(
                componentContext = componentContext,
                onLibrariesClicked = {
                    analytics.logEvent(eventName = "settings_libraries_clicked", params = null)
                    navigation.push(Config.Libraries)
                },
                onBackClicked = ::onBackClicked,
            )
        )

        Config.Libraries -> SettingsComponent.Child.Libraries(
            LibrariesSettingsComponentImpl(
                componentContext = componentContext,
                onBackClicked = ::onBackClicked,
            )
        )
    }

    private class MainSettingsComponentImpl(
        componentContext: ComponentContext,
        private val store: SettingsStore,
        private val onMoreClicked: () -> Unit,
        private val onBackClicked: () -> Unit,
    ) : MainSettingsComponent, ComponentContext by componentContext {

        override val model: Value<MainSettingsComponent.Model> =
            store.asValue().map(stateToModel)

        override fun onSoundToggled(enabled: Boolean) {
            store.accept(SettingsStore.Intent.SetSound(enabled))
        }

        override fun onVibrationToggled(enabled: Boolean) {
            store.accept(SettingsStore.Intent.SetVibration(enabled))
        }

        override fun onDarkThemeToggled(enabled: Boolean) {
            store.accept(SettingsStore.Intent.SetDark(enabled))
        }

        override fun onMoreClicked() = onMoreClicked.invoke()

        override fun onBackClicked() = onBackClicked.invoke()
    }

    private class MoreSettingsComponentImpl(
        componentContext: ComponentContext,
        private val onLibrariesClicked: () -> Unit,
        private val onBackClicked: () -> Unit,
    ) : MoreSettingsComponent, ComponentContext by componentContext {

        override fun onLibrariesClicked() = onLibrariesClicked.invoke()
        override fun onBackClicked() = onBackClicked.invoke()
    }

    private class LibrariesSettingsComponentImpl(
        componentContext: ComponentContext,
        private val onBackClicked: () -> Unit,
    ) : LibrariesSettingsComponent, ComponentContext by componentContext {

        override val libraries: List<LibrariesSettingsComponent.Library> = DEFAULT_LIBRARIES

        override fun onBackClicked() = onBackClicked.invoke()
    }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Main : Config

        @Serializable
        data object More : Config

        @Serializable
        data object Libraries : Config
    }

    private companion object {
        val DEFAULT_LIBRARIES: List<LibrariesSettingsComponent.Library> = listOf(
            LibrariesSettingsComponent.Library(
                name = "Kotlin Multiplatform",
                description = "Kotlin language and standard library",
                url = "https://kotlinlang.org/",
            ),
            LibrariesSettingsComponent.Library(
                name = "Compose Multiplatform",
                description = "JetBrains' cross-platform Compose UI",
                url = "https://github.com/JetBrains/compose-multiplatform",
            ),
            LibrariesSettingsComponent.Library(
                name = "Material 3",
                description = "Material Design 3 components for Compose",
                url = "https://m3.material.io/",
            ),
            LibrariesSettingsComponent.Library(
                name = "Decompose",
                description = "Lifecycle-aware navigation and components",
                url = "https://github.com/arkivanov/Decompose",
            ),
            LibrariesSettingsComponent.Library(
                name = "Essenty",
                description = "Lifecycle, state-keeper, back-handler primitives",
                url = "https://github.com/arkivanov/Essenty",
            ),
            LibrariesSettingsComponent.Library(
                name = "MVIKotlin",
                description = "Multiplatform MVI framework",
                url = "https://github.com/arkivanov/MVIKotlin",
            ),
            LibrariesSettingsComponent.Library(
                name = "Metro",
                description = "Compile-time dependency injection",
                url = "https://github.com/ZacSweers/metro",
            ),
            LibrariesSettingsComponent.Library(
                name = "kotlinx.coroutines",
                description = "Asynchronous programming for Kotlin",
                url = "https://github.com/Kotlin/kotlinx.coroutines",
            ),
            LibrariesSettingsComponent.Library(
                name = "kotlinx.serialization",
                description = "Kotlin multiplatform serialization",
                url = "https://github.com/Kotlin/kotlinx.serialization",
            ),
            LibrariesSettingsComponent.Library(
                name = "kotlinx.datetime",
                description = "Multiplatform date and time library",
                url = "https://github.com/Kotlin/kotlinx-datetime",
            ),
            LibrariesSettingsComponent.Library(
                name = "Multiplatform Settings",
                description = "Key-value storage for KMP",
                url = "https://github.com/russhwolf/multiplatform-settings",
            ),
            LibrariesSettingsComponent.Library(
                name = "ConfettiKit",
                description = "Confetti animations for Compose Multiplatform",
                url = "https://github.com/vinceglb/ConfettiKit",
            ),
            LibrariesSettingsComponent.Library(
                name = "GitLive Firebase",
                description = "Multiplatform Firebase SDK wrapper",
                url = "https://github.com/GitLiveApp/firebase-kotlin-sdk",
            ),
            LibrariesSettingsComponent.Library(
                name = "Google Play Services Ads",
                description = "AdMob SDK for Android",
                url = "https://developers.google.com/admob/android/quick-start",
            ),
            LibrariesSettingsComponent.Library(
                name = "Google Play In-App Review",
                description = "Native review prompt on Android",
                url = "https://developer.android.com/guide/playcore/in-app-review",
            ),
        )
    }
}

@Inject
internal class DefaultSettingsComponentFactory(
    private val storeFactory: SettingsStoreFactory,
    private val analytics: AnalyticRepository,
) : SettingsComponent.Factory {
    override fun create(
        componentContext: ComponentContext,
        onBackClicked: () -> Unit,
    ): SettingsComponent = DefaultSettingsComponent(
        componentContext = componentContext,
        storeFactory = storeFactory,
        analytics = analytics,
        onBackClickedCb = onBackClicked,
    )
}
