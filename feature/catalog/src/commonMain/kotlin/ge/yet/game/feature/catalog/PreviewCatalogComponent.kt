package ge.yet.game.feature.catalog

import com.app.common.decompose.PreviewComponentContext
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.compose.MiniAppManifest

class PreviewCatalogComponent : CatalogComponent, ComponentContext by PreviewComponentContext {
    override val model: Value<CatalogComponent.Model>
        get() = MutableValue(CatalogComponent.Model(manifests))

    override fun onPlayClicked(id: MiniAppId) = Unit
}

internal val manifests = listOf<MiniAppManifest>(

)