package ge.yet.game.twentyfortyeight.ui.common

import androidx.compose.runtime.Composable
import ge.yet.game.twentyfortyeight.generated.resources.Res
import ge.yet.game.twentyfortyeight.generated.resources.moves_count
import ge.yet.game.twentyfortyeight.generated.resources.new_game_not_saved
import ge.yet.game.twentyfortyeight.generated.resources.progress_not_saved
import ge.yet.game.twentyfortyeight.store.UiErrorCode
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun errorText(code: UiErrorCode): String = when (code) {
    UiErrorCode.ProgressNotSaved -> stringResource(Res.string.progress_not_saved)
    UiErrorCode.NewGameNotSaved -> stringResource(Res.string.new_game_not_saved)
}

@Composable
internal fun movesValue(value: Long): String = pluralStringResource(
    Res.plurals.moves_count,
    value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
    value,
)
