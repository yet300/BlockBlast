package ge.yet.sample.counter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        onPlayMusicClicked = component::onPlayMusicClicked,
        onStopMusicClicked = component::onStopMusicClicked,
        onIntensityChanged = component::onIntensityChanged,
        onSoundEffectClicked = component::onSoundEffectClicked,
        modifier = modifier,
    )
}

@Composable
private fun CounterContent(
    model: CounterComponent.Model,
    visibility: MiniAppVisibility,
    onIncrementClicked: () -> Unit,
    onPlayMusicClicked: () -> Unit,
    onStopMusicClicked: () -> Unit,
    onIntensityChanged: (Float) -> Unit,
    onSoundEffectClicked: (CounterComponent.SoundEffect) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(text = "Count: ${model.count}")
        Text(text = "Visibility: ${visibility.name}")
        Button(onClick = onIncrementClicked) {
            Text(text = "Increment")
        }
        Spacer(Modifier.height(24.dp))
        Text(text = "Procedural audio (no audio assets)")
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onPlayMusicClicked, enabled = !model.musicPlaying) {
                Text(text = "Play ocean")
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onStopMusicClicked, enabled = model.musicPlaying) {
                Text(text = "Stop")
            }
        }
        Text(text = "Intensity: ${model.intensity}")
        Slider(
            value = model.intensity,
            onValueChange = onIntensityChanged,
            enabled = model.musicPlaying,
        )
        CounterComponent.SoundEffect.entries.chunked(2).forEach { effects ->
            Row(modifier = Modifier.fillMaxWidth()) {
                effects.forEach { effect ->
                    Button(
                        onClick = { onSoundEffectClicked(effect) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = effect.name.lowercase().replaceFirstChar(Char::uppercase))
                    }
                    if (effect != effects.last()) Spacer(Modifier.width(12.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
