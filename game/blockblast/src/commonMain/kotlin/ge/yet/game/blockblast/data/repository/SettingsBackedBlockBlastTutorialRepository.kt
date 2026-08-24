package ge.yet.game.blockblast.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.blockblast.domain.repository.BlockBlastTutorialRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@SingleIn(AppScope::class)
@Inject
internal class SettingsBackedBlockBlastTutorialRepository(
    private val storage: BlockBlastStorage,
    scope: CoroutineScope,
) : BlockBlastTutorialRepository {

    override val tutorialSeen: StateFlow<Boolean> =
        storage.observeBoolean(KEY_TUTORIAL_SEEN, defaultValue = false)
            .stateIn(scope, SharingStarted.Eagerly, false)

    override suspend fun markSeen() = storage.putBoolean(KEY_TUTORIAL_SEEN, true)

    private companion object {
        const val KEY_TUTORIAL_SEEN = "tutorial_seen"
    }
}
