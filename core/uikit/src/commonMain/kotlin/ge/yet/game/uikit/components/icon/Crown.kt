package ge.yet.game.uikit.components.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Crown: ImageVector
    get() {
        if (_Crown != null) {
            return _Crown!!
        }
        _Crown = ImageVector.Builder(
            name = "VipCrownFill",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(2.005f, 19f)
                horizontalLineTo(22.005f)
                verticalLineTo(21f)
                horizontalLineTo(2.005f)
                verticalLineTo(19f)
                close()
                moveTo(2.005f, 5f)
                lineTo(7.005f, 8f)
                lineTo(12.005f, 2f)
                lineTo(17.005f, 8f)
                lineTo(22.005f, 5f)
                verticalLineTo(17f)
                horizontalLineTo(2.005f)
                verticalLineTo(5f)
                close()
            }
        }.build()

        return _Crown!!
    }

@Suppress("ObjectPropertyName")
private var _Crown: ImageVector? = null
