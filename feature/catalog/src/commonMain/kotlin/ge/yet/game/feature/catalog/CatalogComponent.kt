package ge.yet.game.feature.catalog

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.compose.MiniAppManifest

interface CatalogComponent {
    val model: Value<Model>

    fun onPlayClicked(id: MiniAppId)

    data class Model(
        val manifests: List<MiniAppManifest>,
    )

    fun interface Factory {
        fun create(
            componentContext: ComponentContext,
            onPlay: (MiniAppId) -> Unit,
        ): CatalogComponent
    }
}
