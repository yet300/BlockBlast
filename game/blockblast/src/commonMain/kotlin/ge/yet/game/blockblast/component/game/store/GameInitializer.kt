package ge.yet.game.blockblast.component.game.store

import ge.yet.game.blockblast.domain.engine.GameSessionReducer
import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.blockblast.domain.model.RoundStartInfo
import ge.yet.game.blockblast.domain.repository.GameSaveRepository
import ge.yet.game.blockblast.domain.repository.BlockBlastTutorialRepository
import ge.yet.game.domain.repository.SettingsRepository

/**
 * Decides what to do at game bootstrap: start a fresh round, restore a saved
 * one, or restore the terminal snapshot beneath Result. Returns a complete
 * state; it never mutates a long-lived session object.
 */
internal class GameInitializer(
    private val gameReducer: GameSessionReducer,
    private val saveRepository: GameSaveRepository,
    private val settings: SettingsRepository,
    private val tutorialRepository: BlockBlastTutorialRepository,
) {
    enum class Source(val tag: String) {
        New("new"),
        Continue("continue"),
        ResultRestore("result_restore"),
    }

    data class Result(
        val state: GameState,
        val source: Source,
        val roundStart: RoundStartInfo? = null,
    )

    suspend fun initialize(
        isNewGame: Boolean,
        restoredResultState: GameState? = null,
        newGameSeed: Long? = null,
    ): Result {
        if (restoredResultState != null) {
            return Result(
                state = gameReducer.restoreResult(restoredResultState),
                source = Source.ResultRestore,
            )
        }

        val saved = if (isNewGame) null else saveRepository.load()
        if (saved != null && !saved.isGameOver && saved.currentPieces.isNotEmpty()) {
            return Result(
                state = gameReducer.restore(saved, settings.bestScore.value),
                source = Source.Continue,
            )
        }

        val previousState = saved ?: GameState(
            bestScore = settings.bestScore.value,
            bestAtRoundStart = settings.bestScore.value,
        )
        val roundStart = gameReducer.startNewGame(
            previousState = previousState,
            seed = newGameSeed,
            bestScore = maxOf(previousState.bestScore, settings.bestScore.value),
            allowStarterLayout = tutorialRepository.tutorialSeen.value,
        )
        return Result(
            state = roundStart.state,
            source = Source.New,
            roundStart = roundStart.info,
        )
    }
}
