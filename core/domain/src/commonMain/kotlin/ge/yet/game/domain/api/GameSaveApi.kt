package ge.yet.game.domain.api

/**
 * Game-agnostic save contract used by application features.
 *
 * Concrete games keep their save format and resumability rules private and
 * expose only whether the current game can be continued.
 */
interface GameSaveApi {
    suspend fun hasSavedGame(): Boolean
}
