package ge.yet.game.feature.catalog.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogLayoutPolicyTest {
    @Test
    fun catalog_uses_one_column_below_expanded_width() {
        assertEquals(1, catalogColumnCount(839.dp))
    }

    @Test
    fun catalog_uses_two_columns_at_and_above_expanded_width() {
        assertEquals(2, catalogColumnCount(840.dp))
        assertEquals(2, catalogColumnCount(1600.dp))
    }

    @Test
    fun catalog_padding_expands_with_the_grid() {
        assertEquals(16.dp, catalogOuterPadding(600.dp))
        assertEquals(24.dp, catalogOuterPadding(840.dp))
    }

    @Test
    fun catalog_content_width_is_capped_on_large_screens() {
        assertEquals(1200.dp, catalogContentWidth(1600.dp))
    }

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
