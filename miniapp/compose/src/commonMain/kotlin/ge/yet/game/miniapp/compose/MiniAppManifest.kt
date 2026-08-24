package ge.yet.game.miniapp.compose

import ge.yet.game.miniapp.api.MiniAppCategoryId
import ge.yet.game.miniapp.api.MiniAppId
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

data class MiniAppManifest(
    val id: MiniAppId,
    val title: StringResource,
    val description: StringResource,
    val icon: DrawableResource,
    val cover: DrawableResource?,
    val category: MiniAppCategoryId,
    val sortPriority: Int,
)
