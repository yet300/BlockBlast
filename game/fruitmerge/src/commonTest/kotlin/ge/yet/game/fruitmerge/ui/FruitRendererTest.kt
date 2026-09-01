package ge.yet.game.fruitmerge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import ge.yet.game.fruitmerge.engine.FruitLevel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FruitRendererTest {
    @Test
    fun `physical angle rotates the complete fruit character`() = runComposeUiTest {
        setContent {
            Row {
                FruitCanvas(angleRadians = 0f, tag = "fruit_zero")
                FruitCanvas(angleRadians = PI.toFloat(), tag = "fruit_half_turn")
            }
        }

        val zero = onNodeWithTag("fruit_zero").captureToImage().toPixelMap()
        val halfTurn = onNodeWithTag("fruit_half_turn").captureToImage().toPixelMap()
        var mismatchedPixels = 0
        val pixelCount = zero.width * zero.height

        for (y in 0 until zero.height) {
            for (x in 0 until zero.width) {
                val expected = zero[zero.width - 1 - x, zero.height - 1 - y]
                val actual = halfTurn[x, y]
                if (expected.distanceFrom(actual) > 0.15f) mismatchedPixels += 1
            }
        }

        val mismatchRatio = mismatchedPixels.toFloat() / pixelCount
        assertTrue(
            mismatchRatio < 0.05f,
            "A half turn must rotate body, face, and stem together; mismatch ratio was $mismatchRatio",
        )
    }
}

@Composable
private fun FruitCanvas(
    angleRadians: Float,
    tag: String,
) {
    Canvas(Modifier.size(144.dp).testTag(tag)) {
        drawFruit(
            level = FruitLevel.APPLE,
            center = center,
            radius = size.minDimension * 0.30f,
            angleRadians = angleRadians,
            verticalVelocity = 0f,
            impact = 0f,
            facePhase = 1f,
            danger = DangerVisual(intensity = 0f, crying = false),
            alpha = 1f,
        )
    }
}

private fun Color.distanceFrom(other: Color): Float =
    abs(red - other.red) + abs(green - other.green) +
        abs(blue - other.blue) + abs(alpha - other.alpha)
