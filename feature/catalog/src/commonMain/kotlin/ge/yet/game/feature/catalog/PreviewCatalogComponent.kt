package ge.yet.game.feature.catalog

import com.app.common.decompose.PreviewComponentContext
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import ge.yet.game.feature.catalog.generated.resources.Res
import ge.yet.game.feature.catalog.generated.resources.app_name
import ge.yet.game.feature.catalog.generated.resources.catalog_empty_title
import ge.yet.game.feature.catalog.generated.resources.catalog_placeholder
import ge.yet.game.miniapp.api.MiniAppCategoryId
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.compose.MiniAppManifest

class PreviewCatalogComponent : CatalogComponent, ComponentContext by PreviewComponentContext {
    override val model: Value<CatalogComponent.Model> = MutableValue(CatalogComponent.Model(manifests))

    override fun onPlayClicked(id: MiniAppId) = Unit
}

internal val manifests = listOf(
    MiniAppManifest(
        id = MiniAppId("game.preview"),
        title = Res.string.app_name,
        description = Res.string.catalog_empty_title,
        icon = Res.drawable.catalog_placeholder,
        cover = null,
        category = MiniAppCategoryId("game"),
        sortPriority = 0,
    ),
)