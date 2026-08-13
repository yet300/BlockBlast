package ge.yet.game.blockblast.domain.repository

import kotlinx.coroutines.flow.StateFlow

internal interface BlockBlastTutorialRepository {
    val tutorialSeen: StateFlow<Boolean>

    suspend fun markSeen()
}
