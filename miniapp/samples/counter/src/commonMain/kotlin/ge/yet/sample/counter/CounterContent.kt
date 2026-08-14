package ge.yet.sample.counter

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.game.miniapp.api.MiniAppVisibility

@Composable
internal fun CounterContent(
    component: CounterComponent,
    modifier: Modifier = Modifier,
) {
    val model by component.model.subscribeAsState()
    val visibility by component.visibility.collectAsState()

    CounterContent(
        model = model,
        visibility = visibility,
        onIncrementClicked = component::onIncrementClicked,
        modifier = modifier,
    )
}

@Composable
private fun CounterContent(
    model: CounterComponent.Model,
    visibility: MiniAppVisibility,
    onIncrementClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = "Count: ${model.count}")
        Text(text = "Visibility: ${visibility.name}")
        Button(onClick = onIncrementClicked) {
            Text(text = "Increment")
        }
    }
}
