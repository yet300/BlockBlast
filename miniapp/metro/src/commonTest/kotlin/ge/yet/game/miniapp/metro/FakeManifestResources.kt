package ge.yet.game.miniapp.metro

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.StringResource

@OptIn(InternalResourceApi::class)
internal val fakeTitle = StringResource("test:title", "title", emptySet())

@OptIn(InternalResourceApi::class)
internal val fakeDescription = StringResource("test:description", "description", emptySet())

@OptIn(InternalResourceApi::class)
internal val fakeIcon = DrawableResource("test:icon", emptySet())
