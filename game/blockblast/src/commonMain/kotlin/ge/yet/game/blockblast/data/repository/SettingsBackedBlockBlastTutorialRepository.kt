package ge.yet.game.blockblast.data.repository

import com.app.common.AppDispatchers
import com.russhwolf.settings.Settings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.blockblast.domain.repository.BlockBlastTutorialRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
@Inject
internal class SettingsBackedBlockBlastTutorialRepository(
    private val settings: Settings,
    private val dispatchers: AppDispatchers,
) : BlockBlastTutorialRepository {

    private val mutableTutorialSeen = MutableStateFlow(
        settings.getBoolean(KEY_TUTORIAL_SEEN, defaultValue = false),
    )

    override val tutorialSeen: StateFlow<Boolean> = mutableTutorialSeen.asStateFlow()

    override suspend fun markSeen() = withContext(dispatchers.io) {
        settings.putBoolean(KEY_TUTORIAL_SEEN, true)
        mutableTutorialSeen.value = true
    }

    private companion object {
        const val KEY_TUTORIAL_SEEN = "blockblast.tutorial_seen"
    }
}
