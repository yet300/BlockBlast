package ge.yet.game.blockblast.session

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import dev.zacsweers.metro.Inject
import ge.yet.game.blockblast.component.game.GameComponent
import ge.yet.game.blockblast.component.result.BlockBlastResultSnapshot
import ge.yet.game.blockblast.component.result.GameResultComponent
import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.miniapp.api.MiniAppReviewOpportunity
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.compose.MiniAppFrameMode

internal class DefaultBlockBlastSessionComponent(
    componentContext: ComponentContext,
    private val gameFactory: GameComponent.Factory,
    private val resultFactory: GameResultComponent.Factory,
    @Suppress("UNUSED_PARAMETER") visibility: MiniAppVisibilitySource,
    private val host: MiniAppSessionHost,
) : BlockBlastSessionComponent,
    ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()
    private var lastGameInstanceId = 1L

    override val stack: Value<ChildStack<*, BlockBlastSessionComponent.Child>> = childStack(
        source = navigation,
        serializer = null,
        initialConfiguration = Config.Playing(
            instanceId = 1L,
            isNewGame = false,
            restoredResultState = null,
        ),
        handleBackButton = false,
        childFactory = ::createChild,
    )

    override val frameMode: Value<MiniAppFrameMode> = stack.map { childStack ->
        when (childStack.active.instance) {
            is BlockBlastSessionComponent.Child.Playing -> MiniAppFrameMode.Standard
            is BlockBlastSessionComponent.Child.Result -> MiniAppFrameMode.ContentOnly
        }
    }

    private fun createChild(
        config: Config,
        componentContext: ComponentContext,
    ): BlockBlastSessionComponent.Child = when (config) {
        is Config.Playing -> {
            lastGameInstanceId = maxOf(lastGameInstanceId, config.instanceId)
            BlockBlastSessionComponent.Child.Playing(
                gameFactory.create(
                    componentContext = componentContext,
                    isNewGame = config.isNewGame,
                    restoredResultState = config.restoredResultState,
                    onGameCompleted = { finalState, canContinue, reviewOpportunity ->
                        showResult(
                            gameInstanceId = config.instanceId,
                            finalState = finalState,
                            canContinue = canContinue,
                            reviewOpportunity = reviewOpportunity,
                        )
                    },
                    onReviveCompleted = { playableState ->
                        finishContinue(config.instanceId, playableState)
                    },
                    onReviveFailed = { failContinue(config.instanceId) },
                ),
            )
        }

        is Config.Result -> BlockBlastSessionComponent.Child.Result(
            resultFactory.create(
                componentContext = componentContext,
                snapshot = BlockBlastResultSnapshot.from(config.finalState),
                canContinue = config.canContinue,
                onContinueRequested = { continueGame(config) },
                onNewGameRequested = { startNewGame(config) },
            ),
        )
    }

    private fun continueGame(resultConfig: Config.Result) {
        if (!isActiveResult(resultConfig)) return
        val game = stack.value.items
            .asReversed()
            .firstNotNullOfOrNull { child ->
                val config = child.configuration as? Config.Playing
                if (config?.instanceId == resultConfig.gameInstanceId) {
                    (child.instance as? BlockBlastSessionComponent.Child.Playing)?.component
                } else {
                    null
                }
            }
        game?.onReviveClicked()
    }

    private fun finishContinue(
        gameInstanceId: Long,
        playableState: GameState,
    ) {
        if (playableState.isGameOver) return
        navigation.navigate { configurations ->
            val activeResult = configurations.lastOrNull() as? Config.Result
            if (
                activeResult?.gameInstanceId != gameInstanceId ||
                configurations.none {
                    it is Config.Playing && it.instanceId == gameInstanceId
                }
            ) {
                configurations
            } else {
                configurations.dropLast(1)
            }
        }
    }

    private fun failContinue(gameInstanceId: Long) {
        val active = stack.value.active
        val config = active.configuration as? Config.Result
        if (config?.gameInstanceId != gameInstanceId) return
        (active.instance as? BlockBlastSessionComponent.Child.Result)
            ?.component
            ?.onContinueFailed()
    }

    private fun startNewGame(resultConfig: Config.Result) {
        if (!isActiveResult(resultConfig)) return
        navigation.replaceAll(
            Config.Playing(
                instanceId = ++lastGameInstanceId,
                isNewGame = true,
                restoredResultState = null,
            ),
        )
    }

    private fun isActiveResult(config: Config.Result): Boolean =
        stack.value.active.configuration == config

    private fun showResult(
        gameInstanceId: Long,
        finalState: GameState,
        canContinue: Boolean,
        reviewOpportunity: Boolean,
    ) {
        var added = false
        navigation.navigate { configurations ->
            if (configurations.lastOrNull() is Config.Result) return@navigate configurations
            if (
                configurations.none {
                    it is Config.Playing && it.instanceId == gameInstanceId
                }
            ) {
                return@navigate configurations
            }
            added = true
            configurations.map { config ->
                if (config is Config.Playing && config.instanceId == gameInstanceId) {
                    config.copy(restoredResultState = finalState)
                } else {
                    config
                }
            } + Config.Result(gameInstanceId, finalState, canContinue)
        }
        if (added && reviewOpportunity) {
            host.requestReview(
                MiniAppReviewOpportunity(
                    triggerId = "block_blast_result",
                    score = finalState.score,
                    bestScore = finalState.bestScore,
                    revivesUsed = finalState.revivesUsed,
                ),
            )
        }
    }
}

@Inject
internal class DefaultBlockBlastSessionComponentFactory(
    private val gameFactory: GameComponent.Factory,
    private val resultFactory: GameResultComponent.Factory,
) : BlockBlastSessionComponent.Factory {
    override fun create(
        componentContext: ComponentContext,
        visibility: MiniAppVisibilitySource,
        host: MiniAppSessionHost,
    ): BlockBlastSessionComponent = DefaultBlockBlastSessionComponent(
        componentContext = componentContext,
        gameFactory = gameFactory,
        resultFactory = resultFactory,
        visibility = visibility,
        host = host,
    )
}

private sealed interface Config {
    data class Playing(
        val instanceId: Long,
        val isNewGame: Boolean,
        val restoredResultState: GameState?,
    ) : Config

    data class Result(
        val gameInstanceId: Long,
        val finalState: GameState,
        val canContinue: Boolean,
    ) : Config
}
