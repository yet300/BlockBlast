package ge.yet.game.feature.catalog.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogLayoutPolicyTest {
    @Test
    fun safe_drawing_insets_are_added_to_content_padding_once() {
        val padding = catalogContentPadding(
            contentPadding = 20.dp,
            safeTop = 24.dp,
            safeBottom = 16.dp,
        )

        assertEquals(20.dp, padding.start)
        assertEquals(20.dp, padding.end)
        assertEquals(44.dp, padding.top)
        assertEquals(36.dp, padding.bottom)
    }
}
