package ge.yet.game.miniapp.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.Cancellation
import com.arkivanov.decompose.value.Value

enum class MiniAppFrameMode {
    Standard,
    ContentOnly,
}

private object StandardMiniAppFrameMode : Value<MiniAppFrameMode>() {
    override val value: MiniAppFrameMode = MiniAppFrameMode.Standard

    override fun subscribe(observer: (MiniAppFrameMode) -> Unit): Cancellation {
        observer(value)
        return Cancellation {}
    }
}

interface MiniAppSession {

    val frameMode: Value<MiniAppFrameMode>
        get() = StandardMiniAppFrameMode

    @Composable
    fun TopBarContent() = Unit

    @Composable
    fun Content(modifier: Modifier)
}
