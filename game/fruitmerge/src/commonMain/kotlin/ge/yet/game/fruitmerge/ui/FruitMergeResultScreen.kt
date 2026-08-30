package ge.yet.game.fruitmerge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.game.fruitmerge.generated.resources.Res
import ge.yet.game.fruitmerge.generated.resources.best_score
import ge.yet.game.fruitmerge.generated.resources.game_over
import ge.yet.game.fruitmerge.generated.resources.new_game
import ge.yet.game.fruitmerge.generated.resources.score
import ge.yet.game.fruitmerge.session.FruitMergeResultComponent
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FruitMergeResultScreen(
    component: FruitMergeResultComponent,
    modifier: Modifier = Modifier,
) {
    val model by component.model.subscribeAsState()
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(Res.string.game_over), style = MaterialTheme.typography.headlineLarge)
        Text("${stringResource(Res.string.score)} ${model.score}")
        Text("${stringResource(Res.string.best_score)} ${model.bestScore}")
        Button(onClick = component::newGame) {
            Text(stringResource(Res.string.new_game))
        }
    }
}
