package ge.yet.game.fruitmerge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import ge.yet.game.fruitmerge.engine.FruitBody
import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.engine.FruitMergeState
import ge.yet.game.fruitmerge.engine.RunPhase
import ge.yet.game.fruitmerge.engine.Vec2
import ge.yet.game.fruitmerge.session.FruitMergeComponent
import ge.yet.game.fruitmerge.session.PaidAction
import ge.yet.game.fruitmerge.session.PaidActionToken
import ge.yet.game.uikit.theme.LogicaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FruitMergeScreenTest {
    @Test
    fun `compact layout keeps board and consumable actions reachable`() = runComposeUiTest {
        val component = FakeFruitMergeComponent(playingModel())
        setContent {
            LogicaTheme(darkTheme = false) {
                Box(Modifier.size(390.dp, 760.dp)) {
                    FruitMergeScreen(component, {}, {})
                }
            }
        }

        onNodeWithTag(FruitMergeTestTags.Board).assertIsDisplayed()
        onNodeWithTag(FruitMergeTestTags.Clear).assertIsDisplayed()
        onNodeWithTag(FruitMergeTestTags.Shake).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `wide layout places supporting actions beside the board`() = runComposeUiTest {
        val component = FakeFruitMergeComponent(playingModel())
        setContent {
            LogicaTheme(darkTheme = false) {
                Box(Modifier.size(1000.dp, 620.dp)) {
                    FruitMergeScreen(component, {}, {})
                }
            }
        }

        val board = onNodeWithTag(FruitMergeTestTags.Board).fetchSemanticsNode().boundsInRoot
        val support = onNodeWithTag(FruitMergeTestTags.Support).fetchSemanticsNode().boundsInRoot
        assertTrue(board.center.x < support.center.x)
    }

    @Test
    fun `exhausted clear delegates the exact paid token to the ad gate`() = runComposeUiTest {
        val token = PaidActionToken(sessionKey = 7, runOrdinal = 3, id = 11, action = PaidAction.CLEAR)
        val component = FakeFruitMergeComponent(
            playingModel().copy(game = playingModel().game.copy(freeClears = 0)),
            clearToken = token,
        )
        var requested: PaidActionToken? = null
        setContent {
            LogicaTheme(darkTheme = false) {
                Box(Modifier.size(390.dp, 760.dp)) {
                    FruitMergeScreen(component, { requested = it }, {})
                }
            }
        }

        onNodeWithTag(FruitMergeTestTags.Clear).performClick()

        assertEquals(token, requested)
        assertEquals(1, component.clearRequests)
    }

    @Test
    fun `result presents a new game action`() = runComposeUiTest {
        val component = FakeFruitMergeComponent(
            playingModel().copy(game = playingModel().game.copy(phase = RunPhase.RESULT)),
        )
        setContent {
            LogicaTheme(darkTheme = false) {
                Box(Modifier.size(390.dp, 760.dp)) {
                    FruitMergeScreen(component, {}, {})
                }
            }
        }

        onNodeWithTag(FruitMergeTestTags.NewGame).assertIsDisplayed().performClick()
        assertEquals(1, component.newGameCalls)
    }

    private fun playingModel(): FruitMergeComponent.Model = FruitMergeComponent.Model(
        game = FruitMergeState(
            bodies = listOf(FruitBody(1, FruitLevel.APPLE, Vec2(0.5f, 0.8f))),
            nextBodyId = 2,
        ),
        initialized = true,
        visible = false,
    )
}

private class FakeFruitMergeComponent(
    initial: FruitMergeComponent.Model,
    private val clearToken: PaidActionToken? = null,
) : FruitMergeComponent {
    private val mutableModel = MutableValue(initial)
    override val model: Value<FruitMergeComponent.Model> = mutableModel

    var clearRequests = 0
    var newGameCalls = 0

    override fun frame(elapsedSeconds: Float) = Unit
    override fun movePreview(x: Float) = Unit
    override fun drop(dragged: Boolean) = Unit

    override fun requestClearGate(): PaidActionToken? {
        clearRequests += 1
        return clearToken
    }

    override fun selectClearTarget(id: Long) = Unit
    override fun cancelClear() = Unit
    override fun requestShakeGate(): PaidActionToken? = null
    override fun completePaidAction(token: PaidActionToken) = Unit

    override fun newGame() {
        newGameCalls += 1
    }

    override fun skipTutorial() = Unit

    override fun handleBack(): Boolean = false
}
