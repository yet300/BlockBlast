package ge.yet3.blokblast.component.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Anthropic-styled bottom sheet wrapper.
 *
 * Builds on Material3's [ModalBottomSheet] (which gives us the swipe-to-dismiss
 * scrim and gesture handling for free) but layers a custom dialog-style
 * entrance on the *content*: spring scale-up + slide-up + fade.  The handle is
 * rendered manually as a soft warm pill and the container is tinted Parchment
 * with a top-rounded shape to match the literary-salon aesthetic.
 *
 * Use this in place of the older [ModalBottomSheet] wrapper anywhere you want
 * the custom Sina-Samaki-style dialog motion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaudeBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }

    ModalBottomSheet(
        modifier = modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        AnimatedVisibility(
            visible = contentVisible,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { it / 4 } + fadeIn(tween(260)),
            exit = slideOutVertically(animationSpec = tween(180)) { it / 4 } +
                fadeOut(tween(140)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Soft warm handle pill
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 44.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outline)
                        .clickable(onClick =  onDismiss),
                )
                Spacer(Modifier.height(8.dp))

                Box(modifier = Modifier.padding(bottom = 8.dp)) {
                    content()
                }
            }
        }
    }
}
