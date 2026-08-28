package ge.yet.game.twentyfortyeight

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ge.yet.game.twentyfortyeight.session.TwentyFortyEightSessionComponent
import ge.yet.game.twentyfortyeight.ui.TwentyFortyEightScreen

@Composable
internal fun TwentyFortyEightContent(
    component: TwentyFortyEightSessionComponent,
    modifier: Modifier = Modifier,
) = TwentyFortyEightScreen(component = component, modifier = modifier)
