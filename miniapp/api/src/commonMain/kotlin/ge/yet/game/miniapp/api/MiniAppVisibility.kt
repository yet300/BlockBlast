package ge.yet.game.miniapp.api

import kotlinx.coroutines.flow.StateFlow

enum class MiniAppVisibility {
    ACTIVE,
    OBSCURED,
    BACKGROUND,
}

interface MiniAppVisibilitySource {
    val visibility: StateFlow<MiniAppVisibility>
}
