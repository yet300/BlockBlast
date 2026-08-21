package ge.yet.game.screen.miniapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.cd_back
import blockblast.composeapp.generated.resources.cd_settings
import ge.yet.game.miniapp.compose.MiniAppFrameMode
import ge.yet.game.uikit.components.button.IconCircleButton
import ge.yet.game.uikit.components.icon.ArrowBack
import ge.yet.game.uikit.components.icon.Settings
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniAppFrame(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    frameMode: MiniAppFrameMode = MiniAppFrameMode.Standard,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            AnimatedVisibility(
                visible = frameMode == MiniAppFrameMode.Standard,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                CenterAlignedTopAppBar(
                    title = topBar,
                    navigationIcon = {
                        IconCircleButton(
                            icon = ArrowBack,
                            contentDescription = stringResource(Res.string.cd_back),
                            onClick = onBack,
                            modifier = Modifier.testTag("miniapp_back_control"),
                        )
                    },
                    actions = {
                        IconCircleButton(
                            icon = Settings,
                            contentDescription = stringResource(Res.string.cd_settings),
                            onClick = onSettings,
                            modifier = Modifier.testTag("miniapp_settings_control"),
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                )
            }
        },
        bottomBar = {
            if (bottomBar != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .height(MINI_APP_BANNER_HEIGHT_DP.dp)
                        .testTag("miniapp_banner_container"),
                    contentAlignment = Alignment.Center,
                ) {
                    bottomBar()
                }
            }
        },
    ) { padding ->
        content(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        )
    }
}
