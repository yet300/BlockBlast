package ge.yet.game.screen.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.game.feature.root.RootComponent
import ge.yet.game.uikit.components.sheet.ClaudeBottomSheet
import ge.yet.game.screen.review.AppReviewContent
import ge.yet.game.screen.settings.SettingsContent


@Composable
fun RootSheet(component: RootComponent) {
    val dialogSheetSlot by component.sheetSlot.subscribeAsState()

    dialogSheetSlot.child?.instance?.let { child ->
        ClaudeBottomSheet(
            onDismiss = component::onDismissSheet,
        ) {
            when (child) {
                is RootComponent.SheetChild.Settings -> SettingsContent(component = child.component)
                is RootComponent.SheetChild.AppReview -> AppReviewContent(component = child.component)
            }
        }
    }
}