package ge.yet.blockblast.feature.game.store

import ge.yet.blokblast.domain.engine.GameEngine
import ge.yet.blokblast.domain.model.GameState
import ge.yet.blokblast.domain.repository.GameSaveRepository
import ge.yet.blokblast.domain.repository.SettingsRepository

/**
 * Decides what to do at game bootstrap: start a fresh round, restore a saved
 * one, or leave a warm engine alone. Pulled out of GameStoreFactory so the
 * factory only wires concerns together; the branching logic lives here and is
 * unit-testable in isolation.
 */
internal class GameInitializer(
    private val engine: GameEngine,
    private val saveRepository: GameSaveRepository,
    private val settings: SettingsRepository,
) {
    enum class Source(val tag: String) {
        New("new"),
        Continue("continue"),
        ResultRestore("result_restore"),
    }

    fun seedBestScore() {
        engine.seedBestScore(settings.bestScore.value)
    }

    suspend fun initialize(
        isNewGame: Boolean,
        restoredResultState: GameState? = null,
        newGameSeed: Long? = null,
    ): Source {
        if (restoredResultState != null) {
            return Source.ResultRestore
        }

        val current = engine.state.value

        if (isNewGame || current.isGameOver) {
            engine.startNewGame(
                seed = newGameSeed,
                bestScore = current.bestScore,
                allowStarterLayout = settings.tutorialSeen.value,
            )
            return Source.New
        }

        if (current.currentPieces.isEmpty()) {
            val saved = saveRepository.load()
            return if (saved != null && !saved.isGameOver && saved.currentPieces.isNotEmpty()) {
                engine.restore(saved)
                Source.Continue
            } else {
                engine.startNewGame(
                    seed = newGameSeed,
                    bestScore = current.bestScore,
                    allowStarterLayout = settings.tutorialSeen.value,
                )
                Source.New
            }
        }

        // Warm continue: engine already holds an in-flight round; leave it.
        return Source.Continue
    }
}
