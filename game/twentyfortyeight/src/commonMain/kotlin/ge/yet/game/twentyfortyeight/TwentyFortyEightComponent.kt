package ge.yet.game.twentyfortyeight

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy

interface TwentyFortyEightComponent

internal class DefaultTwentyFortyEightComponent(
    componentContext: ComponentContext,
) : TwentyFortyEightComponent, ComponentContext by componentContext {
    init {
        componentContext.lifecycle.doOnDestroy { }
    }
}
