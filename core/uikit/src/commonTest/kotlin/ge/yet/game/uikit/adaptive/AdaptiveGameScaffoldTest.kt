package ge.yet.game.uikit.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AdaptiveGameScaffoldTest {
    @Test
    fun `material breakpoints produce one shared game arrangement`() {
        val compact = adaptiveGameMetrics(599, 800)
        val medium = adaptiveGameMetrics(600, 800)
        val mediumUpperEdge = adaptiveGameMetrics(839, 800)
        val expanded = adaptiveGameMetrics(840, 800)
        val compactHeight = adaptiveGameMetrics(599, 479)
        val narrowCompactHeight = adaptiveGameMetrics(320, 479)
        val compactHeightBelowViability = adaptiveGameMetrics(567, 479)
        val compactHeightAtViability = adaptiveGameMetrics(568, 479)
        val regularHeight = adaptiveGameMetrics(599, 480)

        assertEquals(AdaptiveGameArrangement.Vertical, compact.arrangement)
        assertEquals(AdaptiveGameArrangement.Vertical, medium.arrangement)
        assertEquals(AdaptiveGameArrangement.Vertical, mediumUpperEdge.arrangement)
        assertEquals(AdaptiveGameArrangement.TwoPane, expanded.arrangement)
        assertEquals(AdaptiveGameArrangement.CompactHeightTwoPane, compactHeight.arrangement)
        assertEquals(AdaptiveGameArrangement.Vertical, narrowCompactHeight.arrangement)
        assertEquals(AdaptiveGameArrangement.Vertical, compactHeightBelowViability.arrangement)
        assertEquals(AdaptiveGameArrangement.CompactHeightTwoPane, compactHeightAtViability.arrangement)
        assertEquals(AdaptiveGameArrangement.Vertical, regularHeight.arrangement)

        assertFalse(
            compact.windowSizeClass.isWidthAtLeastBreakpoint(
                WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
            ),
        )
        assertTrue(
            medium.windowSizeClass.isWidthAtLeastBreakpoint(
                WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
            ),
        )
        assertFalse(
            mediumUpperEdge.windowSizeClass.isWidthAtLeastBreakpoint(
                WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
            ),
        )
        assertTrue(
            expanded.windowSizeClass.isWidthAtLeastBreakpoint(
                WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
            ),
        )
        assertFalse(
            compactHeight.windowSizeClass.isHeightAtLeastBreakpoint(
                WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
            ),
        )
        assertTrue(
            regularHeight.windowSizeClass.isHeightAtLeastBreakpoint(
                WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
            ),
        )
    }

    @Test
    fun `non finite viewport dimensions are rejected at the adaptive boundary`() {
        assertFailsWith<IllegalArgumentException> {
            adaptiveGameMetrics(Float.POSITIVE_INFINITY, 800f)
        }
        assertFailsWith<IllegalArgumentException> {
            adaptiveGameMetrics(599f, Float.POSITIVE_INFINITY)
        }
    }

    @Test
    fun `shared metrics enforce primary content and supporting caps`() {
        assertEquals(520, adaptiveGameMetrics(599, 800).primaryMaxDp)
        assertEquals(520, adaptiveGameMetrics(600, 800).primaryMaxDp)
        assertEquals(560, adaptiveGameMetrics(840, 800).primaryMaxDp)
        assertEquals(1200, adaptiveGameMetrics(1400, 900).contentMaxDp)
        assertEquals(280, adaptiveGameMetrics(840, 800).supportingMinDp)
        assertEquals(360, adaptiveGameMetrics(1200, 900).supportingMaxDp)
    }

    @Test
    fun `expanded scaffold enforces primary and supporting pane widths`() = runComposeUiTest {
        setContent {
            AdaptiveGameScaffold(
                modifier = Modifier.size(width = 1200.dp, height = 800.dp),
                supportingPaneModifier = Modifier.testTag("supporting_pane"),
                primary = {
                    Box(Modifier.fillMaxSize().testTag("primary_content"))
                },
                supporting = {
                    Box(Modifier.fillMaxWidth().height(1200.dp).testTag("supporting_content"))
                },
            )
        }

        val primaryWidth = onNodeWithTag("primary_content")
            .fetchSemanticsNode().boundsInRoot.width
        val supportingWidth = onNodeWithTag("supporting_pane")
            .fetchSemanticsNode().boundsInRoot.width
        val primaryMaxPx = with(density) { 560.dp.toPx() }
        val supportingMinPx = with(density) { 280.dp.toPx() }
        val supportingMaxPx = with(density) { 360.dp.toPx() }
        assertTrue(primaryWidth <= primaryMaxPx + 1f)
        assertTrue(supportingWidth in (supportingMinPx - 1f)..(supportingMaxPx + 1f))
    }

    @Test
    fun `primary remains playable across compact medium expanded and height boundaries`() = runComposeUiTest {
        val viewport = mutableStateOf(DpSize(width = 320.dp, height = 479.dp))
        setContent {
            AdaptiveGameScaffold(
                modifier = Modifier.size(viewport.value.width, viewport.value.height),
                supportingPaneModifier = Modifier.testTag("supporting_pane"),
                primary = {
                    Box(Modifier.fillMaxSize().testTag("primary_content"))
                },
                supporting = {
                    Box(Modifier.fillMaxWidth().height(900.dp))
                },
            )
        }

        fun assertPrimaryAtLeast(minimumDp: Int) {
            val bounds = onNodeWithTag("primary_content").fetchSemanticsNode().boundsInRoot
            val minimumPx = with(density) { minimumDp.dp.toPx() }
            assertTrue(bounds.width >= minimumPx - 1f, "primary width ${bounds.width}px < $minimumDp dp")
            assertTrue(bounds.height >= minimumPx - 1f, "primary height ${bounds.height}px < $minimumDp dp")
        }

        assertPrimaryAtLeast(280)
        listOf(
            DpSize(568.dp, 479.dp) to 240,
            DpSize(599.dp, 479.dp) to 240,
            DpSize(599.dp, 480.dp) to 280,
            DpSize(599.dp, 800.dp) to 280,
            DpSize(600.dp, 800.dp) to 280,
            DpSize(840.dp, 800.dp) to 280,
        ).forEach { (size, minimumDp) ->
            runOnIdle { viewport.value = size }
            waitForIdle()
            assertPrimaryAtLeast(minimumDp)
        }

        runOnIdle { viewport.value = DpSize(width = 568.dp, height = 479.dp) }
        waitForIdle()
        val primaryBounds = onNodeWithTag("primary_content").fetchSemanticsNode().boundsInRoot
        val supportingBounds = onNodeWithTag("supporting_pane").fetchSemanticsNode().boundsInRoot
        val supportingWidthPx = with(density) { 280.dp.toPx() }
        assertEquals(primaryBounds.width, primaryBounds.height, absoluteTolerance = 1f)
        assertEquals(supportingWidthPx, supportingBounds.width, absoluteTolerance = 1f)
    }

    @Test
    fun `only supporting pane owns vertical scrolling`() = runComposeUiTest {
        setContent {
            AdaptiveGameScaffold(
                modifier = Modifier.size(width = 599.dp, height = 479.dp),
                supportingPaneModifier = Modifier.testTag("supporting_pane"),
                primary = {
                    Box(Modifier.fillMaxSize().testTag("primary_content"))
                },
                supporting = {
                    Column(Modifier.height(1200.dp).testTag("supporting_content")) {}
                },
            )
        }

        val scrollable = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        onNodeWithTag("supporting_pane").assert(scrollable)
        onNodeWithTag("primary_content").assert(scrollable.not())
    }

    @Test
    fun `supporting scroll position survives a live arrangement change`() = runComposeUiTest {
        val viewport = mutableStateOf(DpSize(width = 599.dp, height = 479.dp))
        setContent {
            AdaptiveGameScaffold(
                modifier = Modifier.size(viewport.value.width, viewport.value.height),
                supportingPaneModifier = Modifier.testTag("supporting_pane"),
                primary = {
                    Box(Modifier.fillMaxSize().testTag("primary_content"))
                },
                supporting = {
                    Box(Modifier.fillMaxWidth().height(1600.dp))
                },
            )
        }

        val supporting = onNodeWithTag("supporting_pane")
        supporting.performTouchInput { swipeUp() }
        val scrollRange = SemanticsProperties.VerticalScrollAxisRange
        val beforeResize = supporting.fetchSemanticsNode().config[scrollRange].value()
        assertTrue(beforeResize > 0f)

        runOnIdle { viewport.value = DpSize(width = 599.dp, height = 480.dp) }
        waitForIdle()

        val afterResize = supporting.fetchSemanticsNode().config[scrollRange].value()
        assertEquals(beforeResize, afterResize, absoluteTolerance = 1f)
    }

    @Test
    fun `live constraints keep one outer root and preserve slot identity`() = runComposeUiTest {
        val viewport = mutableStateOf(DpSize(width = 599.dp, height = 700.dp))
        var initialPrimaryIdentity: Any? = null
        var currentPrimaryIdentity: Any? = null
        var initialSupportingIdentity: Any? = null
        var currentSupportingIdentity: Any? = null

        setContent {
            AdaptiveGameScaffold(
                modifier = Modifier
                    .size(viewport.value.width, viewport.value.height)
                    .testTag("adaptive_game_root"),
                supportingPaneModifier = Modifier.testTag("supporting_pane"),
                primary = {
                    val identity = remember { Any() }
                    SideEffect {
                        if (initialPrimaryIdentity == null) initialPrimaryIdentity = identity
                        currentPrimaryIdentity = identity
                    }
                    Box(Modifier.fillMaxSize().testTag("primary_content"))
                },
                supporting = {
                    val identity = remember { Any() }
                    SideEffect {
                        if (initialSupportingIdentity == null) initialSupportingIdentity = identity
                        currentSupportingIdentity = identity
                    }
                    Box(Modifier.fillMaxWidth().height(900.dp))
                },
            )
        }

        val originalPrimaryIdentity = initialPrimaryIdentity
        val originalSupportingIdentity = initialSupportingIdentity
        runOnIdle {
            viewport.value = DpSize(width = 840.dp, height = 700.dp)
        }

        onAllNodesWithTag("adaptive_game_root").assertCountEquals(1)
        assertSame(originalPrimaryIdentity, currentPrimaryIdentity)
        assertSame(originalSupportingIdentity, currentSupportingIdentity)
        assertNotSame(null, currentPrimaryIdentity)
        assertNotSame(null, currentSupportingIdentity)
    }
}
