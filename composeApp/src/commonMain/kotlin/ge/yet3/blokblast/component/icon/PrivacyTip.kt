package ge.yet3.blokblast.component.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val PrivacyTip: ImageVector
    get() {
        if (_PrivacyTip != null) {
            return _PrivacyTip!!
        }
        _PrivacyTip = ImageVector.Builder(
            name = "PrivacyTip",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(440f, 680f)
                horizontalLineToRelative(80f)
                verticalLineToRelative(-240f)
                horizontalLineToRelative(-80f)
                verticalLineToRelative(240f)
                close()
                moveTo(508.5f, 348.5f)
                quadTo(520f, 337f, 520f, 320f)
                reflectiveQuadToRelative(-11.5f, -28.5f)
                quadTo(497f, 280f, 480f, 280f)
                reflectiveQuadToRelative(-28.5f, 11.5f)
                quadTo(440f, 303f, 440f, 320f)
                reflectiveQuadToRelative(11.5f, 28.5f)
                quadTo(463f, 360f, 480f, 360f)
                reflectiveQuadToRelative(28.5f, -11.5f)
                close()
                moveTo(480f, 880f)
                quadToRelative(-139f, -35f, -229.5f, -159.5f)
                reflectiveQuadTo(160f, 444f)
                verticalLineToRelative(-244f)
                lineToRelative(320f, -120f)
                lineToRelative(320f, 120f)
                verticalLineToRelative(244f)
                quadToRelative(0f, 152f, -90.5f, 276.5f)
                reflectiveQuadTo(480f, 880f)
                close()
                moveTo(480f, 796f)
                quadToRelative(104f, -33f, 172f, -132f)
                reflectiveQuadToRelative(68f, -220f)
                verticalLineToRelative(-189f)
                lineToRelative(-240f, -90f)
                lineToRelative(-240f, 90f)
                verticalLineToRelative(189f)
                quadToRelative(0f, 121f, 68f, 220f)
                reflectiveQuadToRelative(172f, 132f)
                close()
                moveTo(480f, 480f)
                close()
            }
        }.build()

        return _PrivacyTip!!
    }

@Suppress("ObjectPropertyName")
private var _PrivacyTip: ImageVector? = null
