package ge.yet3.blokblast.screen.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.blockblast.feature.game.GameComponent
import ge.yet3.blokblast.component.sheet.ClaudeBottomSheet
import ge.yet3.blokblast.screen.settings.SettingsContent


@Composable
fun GameSheet(component: GameComponent) {
    val dialogSheetSlot by component.sheetSlot.subscribeAsState()

    dialogSheetSlot.child?.instance?.let { child ->
        ClaudeBottomSheet(
            onDismiss = component::onDismissSheet,
        ) {
            when (child) {
                is GameComponent.SheetChild.Settings -> SettingsContent(component = child.component)
            }
        }
    }
}