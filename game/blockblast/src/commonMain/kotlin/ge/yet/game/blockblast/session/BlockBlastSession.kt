package ge.yet.game.blockblast.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import ge.yet.game.blockblast.ui.BlockBlastSessionContent
import ge.yet.game.blockblast.ui.LocalSoundEnabled
import ge.yet.game.blockblast.ui.LocalVibrationEnabled
import ge.yet.game.domain.repository.FeedbackPreferences
import ge.yet.game.miniapp.compose.MiniAppInterstitialCapability
import ge.yet.game.miniapp.compose.MiniAppSession

internal class BlockBlastSession(
    internal val component: BlockBlastSessionComponent,
    private val interstitials: MiniAppInterstitialCapability,
    internal val feedback: FeedbackPreferences,
) : MiniAppSession {
    @Composable
    override fun Content(modifier: Modifier) {
        val vibrationEnabled by feedback.vibrationEnabled.collectAsState()
        val soundEnabled by feedback.sfxEnabled.collectAsState()

        CompositionLocalProvider(
            LocalVibrationEnabled provides vibrationEnabled,
            LocalSoundEnabled provides soundEnabled,
        ) {
            BlockBlastSessionContent(
                component = component,
                interstitials = interstitials,
                modifier = modifier,
            )
        }
    }
}
