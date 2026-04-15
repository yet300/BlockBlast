package ge.yet3.blokblast.component.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IosShare: ImageVector
    get() {
        if (_IosShare != null) {
            return _IosShare!!
        }
        _IosShare = ImageVector.Builder(
            name = "IosShare",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(240f, 880f)
                quadToRelative(-33f, 0f, -56.5f, -23.5f)
                reflectiveQuadTo(160f, 800f)
                verticalLineToRelative(-400f)
                quadToRelative(0f, -33f, 23.5f, -56.5f)
                reflectiveQuadTo(240f, 320f)
                horizontalLineToRelative(120f)
                verticalLineToRelative(80f)
                lineTo(240f, 400f)
                verticalLineToRelative(400f)
                horizontalLineToRelative(480f)
                verticalLineToRelative(-400f)
                lineTo(600f, 400f)
                verticalLineToRelative(-80f)
                horizontalLineToRelative(120f)
                quadToRelative(33f, 0f, 56.5f, 23.5f)
                reflectiveQuadTo(800f, 400f)
                verticalLineToRelative(400f)
                quadToRelative(0f, 33f, -23.5f, 56.5f)
                reflectiveQuadTo(720f, 880f)
                lineTo(240f, 880f)
                close()
                moveTo(440f, 640f)
                verticalLineToRelative(-447f)
                lineToRelative(-64f, 64f)
                lineToRelative(-56f, -57f)
                lineToRelative(160f, -160f)
                lineToRelative(160f, 160f)
                lineToRelative(-56f, 57f)
                lineToRelative(-64f, -64f)
                verticalLineToRelative(447f)
                horizontalLineToRelative(-80f)
                close()
            }
        }.build()

        return _IosShare!!
    }

@Suppress("ObjectPropertyName")
private var _IosShare: ImageVector? = null
