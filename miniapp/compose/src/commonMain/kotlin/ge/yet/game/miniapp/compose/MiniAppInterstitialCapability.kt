package ge.yet.game.miniapp.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

enum class MiniAppInterstitialPlacement {
    CONTINUE_AFTER_GAME_OVER,
    FRUIT_MERGE_CLEAR,
    FRUIT_MERGE_SHAKE,
}

@Immutable
data class MiniAppInterstitialGate(
    val willShowAd: Boolean,
    val request: (onComplete: () -> Unit) -> Unit,
)

interface MiniAppInterstitialCapability {

    @Composable
    fun rememberGate(placement: MiniAppInterstitialPlacement): MiniAppInterstitialGate
}
