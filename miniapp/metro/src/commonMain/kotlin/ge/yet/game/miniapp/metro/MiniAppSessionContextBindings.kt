package ge.yet.game.miniapp.metro

import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.compose.MiniAppSessionContext

@ContributesTo(MiniAppSessionScope::class)
@BindingContainer
object MiniAppSessionContextBindings {
    @Provides
    fun provideComponentContext(context: MiniAppSessionContext): ComponentContext =
        context.componentContext

    @Provides
    fun provideVisibility(context: MiniAppSessionContext): MiniAppVisibilitySource =
        context.visibility

    @Provides
    fun provideHost(context: MiniAppSessionContext): MiniAppSessionHost = context.host

    @Provides
    fun provideStorage(context: MiniAppSessionContext): MiniAppStorage = context.storage

    @Provides
    fun provideAudio(context: MiniAppSessionContext): MiniAppAudio = context.audio
}
