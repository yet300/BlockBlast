package ge.yet.game.feature.catalog

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import dev.zacsweers.metro.Inject
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.compose.MiniAppRegistry

internal class DefaultCatalogComponent(
    componentContext: ComponentContext,
    registry: MiniAppRegistry,
    private val onPlay: (MiniAppId) -> Unit,
) : CatalogComponent,
    ComponentContext by componentContext {
    private val manifests = registry.manifests.toList()
    override val model: Value<CatalogComponent.Model> = MutableValue(
        CatalogComponent.Model(manifests = manifests),
    )

    override fun onPlayClicked(id: MiniAppId) {
        onPlay(id)
    }
}

@Inject
internal class DefaultCatalogComponentFactory(
    private val registry: MiniAppRegistry,
) : CatalogComponent.Factory {
    override fun create(
        componentContext: ComponentContext,
        onPlay: (MiniAppId) -> Unit,
    ): CatalogComponent =
        DefaultCatalogComponent(
            componentContext = componentContext,
            registry = registry,
            onPlay = onPlay,
        )
}
