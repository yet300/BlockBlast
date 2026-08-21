package ge.yet.game.miniapp.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.app.common.di.CommonBindings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import ge.yet.game.data.di.AndroidDataBindings
import ge.yet.game.data.di.DataBindings
import ge.yet.game.domain.repository.AudioFileProvider
import ge.yet.game.di.ComposeAppBindings
import ge.yet.game.feature.catalog.di.CatalogBindings
import ge.yet.game.feature.review.di.ReviewBindings
import ge.yet.game.feature.root.di.RootBindings
import ge.yet.game.feature.settings.di.SettingsBindings
import ge.yet.game.miniapp.metro.MiniAppMetroBindings
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

@DependencyGraph(
    scope = AppScope::class,
    excludes = [MiniAppMetroBindings::class, ComposeAppBindings::class],
    bindingContainers = [
        CommonBindings::class,
        DataBindings::class,
        AndroidDataBindings::class,
        RootBindings::class,
        CatalogBindings::class,
        ReviewBindings::class,
        SettingsBindings::class,
        CounterRootHostBindings::class,
        CounterRootAndroidHostBindings::class,
        CounterRootRegistryBindings::class,
    ],
)
internal interface AndroidCounterRootGraph : CounterRootTestGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context): AndroidCounterRootGraph
    }
}

@BindingContainer
internal object CounterRootAndroidHostBindings {
    @Provides
    fun audioFileProvider(): AudioFileProvider = object : AudioFileProvider {
        override fun path(filename: String): String = filename

        override suspend fun bytes(filename: String): ByteArray? = null
    }
}

internal fun createAndroidCounterRootGraph(): CounterRootTestGraph =
    ApplicationProvider.getApplicationContext<Context>().let { context ->
        val initializer = Class.forName("com.russhwolf.settings.SettingsInitializer")
            .getDeclaredConstructor()
            .newInstance()
        initializer.javaClass.getMethod("create", Context::class.java).invoke(initializer, context)
        createGraphFactory<AndroidCounterRootGraph.Factory>().create(context)
    }

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CounterRootAndroidIntegrationTest {
    private val contract = CounterRootContract(::createAndroidCounterRootGraph)

    @Test
    fun catalog_play_creates_real_counter_session() =
        contract.catalog_play_creates_real_counter_session()

    @Test
    fun settings_keeps_the_same_counter_session_and_reports_obscured() =
        contract.settings_keeps_the_same_counter_session_and_reports_obscured()

    @Test
    fun system_back_dismisses_settings_before_destroying_counter() =
        contract.system_back_dismisses_settings_before_destroying_counter()

    @Test
    fun background_reports_background_then_returns_active() =
        contract.background_reports_background_then_returns_active()

    @Test
    fun back_returns_catalog_and_destroys_counter_lifecycle_once() =
        contract.back_returns_catalog_and_destroys_counter_lifecycle_once()

    @Test
    fun stale_counter_host_callback_cannot_close_a_later_session() =
        contract.stale_counter_host_callback_cannot_close_a_later_session()
}
