package ge.yet.game.fruitmerge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.session.FruitMergeResultComponent
import ge.yet.game.uikit.theme.LogicaTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FruitMergeResultScreenTest {
    @Test
    fun `result is a full screen destination with one new game action`() = runComposeUiTest {
        val component = FakeFruitMergeResultComponent(
            FruitMergeResultComponent.Model(
                score = 1_240,
                bestScore = 2_480,
                largestFruit = FruitLevel.PINEAPPLE,
            ),
        )
        setContent {
            LogicaTheme(darkTheme = false) {
                Box(Modifier.size(390.dp, 760.dp)) {
                    FruitMergeResultScreen(component)
                }
            }
        }

        onNodeWithTag(FruitMergeTestTags.Result).assertIsDisplayed()
        onNodeWithTag(FruitMergeTestTags.ResultScore).assertIsDisplayed()
        onNodeWithTag(FruitMergeTestTags.ResultBest).assertIsDisplayed()
        onNodeWithTag(FruitMergeTestTags.ResultLargestFruit).assertIsDisplayed()
        onNodeWithTag(FruitMergeTestTags.NewGame).performClick()

        assertEquals(1, component.newGameCalls)
    }
}

private class FakeFruitMergeResultComponent(
    initial: FruitMergeResultComponent.Model,
) : FruitMergeResultComponent {
    private val mutableModel = MutableValue(initial)
    override val model: Value<FruitMergeResultComponent.Model> = mutableModel
    var newGameCalls: Int = 0

    override fun newGame() {
        newGameCalls += 1
    }
}
