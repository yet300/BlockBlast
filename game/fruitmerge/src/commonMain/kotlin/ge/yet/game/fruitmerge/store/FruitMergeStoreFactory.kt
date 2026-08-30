package ge.yet.game.fruitmerge.store

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import dev.zacsweers.metro.Inject
import ge.yet.game.fruitmerge.engine.ActionResult
import ge.yet.game.fruitmerge.engine.FruitMergeRules
import ge.yet.game.fruitmerge.engine.FruitMergeState
import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.engine.RunPhase
import ge.yet.game.fruitmerge.persistence.FruitMergePersistence
import kotlinx.coroutines.launch
import kotlin.math.min

@Inject
internal class FruitMergeStoreFactory(
    private val storeFactory: StoreFactory,
    private val rules: FruitMergeRules,
    private val persistence: FruitMergePersistence,
) {
    fun create(): FruitMergeStore =
        object :
            FruitMergeStore,
            Store<FruitMergeStore.Intent, FruitMergeStore.State, FruitMergeStore.Label> by
                storeFactory.create(
                    name = "FruitMergeStore",
                    initialState = FruitMergeStore.State(),
                    bootstrapper = SimpleBootstrapper(Action.Initialize),
                    executorFactory = ::ExecutorImpl,
                    reducer = ReducerImpl,
                ) {}

    private sealed interface Action {
        data object Initialize : Action
    }

    private sealed interface Message {
        data class Initialized(val game: FruitMergeState) : Message
        data class GameChanged(val game: FruitMergeState) : Message
        data class ActiveChanged(val active: Boolean) : Message
    }

    private object ReducerImpl : Reducer<FruitMergeStore.State, Message> {
        override fun FruitMergeStore.State.reduce(msg: Message): FruitMergeStore.State = when (msg) {
            is Message.Initialized -> copy(game = msg.game, initialized = true)
            is Message.GameChanged -> copy(game = msg.game)
            is Message.ActiveChanged -> copy(active = msg.active)
        }
    }

    private inner class ExecutorImpl : CoroutineExecutor<
        FruitMergeStore.Intent,
        Action,
        FruitMergeStore.State,
        Message,
        FruitMergeStore.Label,
        >() {
        private var accumulatorSeconds = 0f

        override fun executeAction(action: Action) {
            when (action) {
                Action.Initialize -> scope.launch {
                    dispatch(Message.Initialized(persistence.restore()))
                }
            }
        }

        override fun executeIntent(intent: FruitMergeStore.Intent) {
            when (intent) {
                is FruitMergeStore.Intent.VisibilityChanged -> visibilityChanged(intent.active)
                FruitMergeStore.Intent.Checkpoint -> checkpoint()
                else -> if (state().initialized) executeReadyIntent(intent)
            }
        }

        private fun executeReadyIntent(intent: FruitMergeStore.Intent) {
            when (intent) {
                is FruitMergeStore.Intent.Frame -> frame(intent.elapsedSeconds)
                is FruitMergeStore.Intent.MovePreview -> replace(rules.movePreview(state().game, intent.x))
                FruitMergeStore.Intent.Drop -> applyAction(
                    result = rules.drop(state().game),
                    checkpoint = true,
                    label = FruitMergeStore.Label.DropAccepted,
                )
                FruitMergeStore.Intent.BeginFreeClear -> applyAction(rules.beginClear(state().game))
                is FruitMergeStore.Intent.ClearBody ->
                    applyAction(
                        result = rules.clear(state().game, intent.id, intent.paid),
                        checkpoint = true,
                        label = FruitMergeStore.Label.ClearApplied,
                    )
                FruitMergeStore.Intent.CancelClear -> replace(rules.cancelClear(state().game))
                FruitMergeStore.Intent.FreeShake -> applyAction(
                    result = rules.shake(state().game),
                    checkpoint = true,
                    label = FruitMergeStore.Label.ShakeApplied,
                )
                FruitMergeStore.Intent.PaidClear -> applyAction(rules.beginClear(state().game, paid = true))
                FruitMergeStore.Intent.PaidShake ->
                    applyAction(
                        result = rules.shake(state().game, paid = true),
                        checkpoint = true,
                        label = FruitMergeStore.Label.ShakeApplied,
                    )
                FruitMergeStore.Intent.NewGame -> {
                    accumulatorSeconds = 0f
                    replace(rules.newRun(state().game), checkpoint = true)
                }
                FruitMergeStore.Intent.Checkpoint,
                is FruitMergeStore.Intent.VisibilityChanged,
                -> Unit
            }
        }

        private fun frame(elapsedSeconds: Float) {
            if (!state().active || !elapsedSeconds.isFinite() || elapsedSeconds <= 0f) return
            accumulatorSeconds = min(
                accumulatorSeconds + elapsedSeconds,
                FIXED_STEP_SECONDS * MAX_STEPS_PER_FRAME,
            )
            var game = state().game
            var steps = 0
            val startingPhase = game.phase
            while (accumulatorSeconds + STEP_EPSILON >= FIXED_STEP_SECONDS && steps < MAX_STEPS_PER_FRAME) {
                val beforeStep = game
                game = rules.step(beforeStep, FIXED_STEP_SECONDS)
                publishMergeLabels(beforeStep, game)
                accumulatorSeconds -= FIXED_STEP_SECONDS
                steps += 1
            }
            if (steps == MAX_STEPS_PER_FRAME) accumulatorSeconds = 0f
            replace(game)
            if (startingPhase == RunPhase.PLAYING && game.phase == RunPhase.RESULT) {
                publish(FruitMergeStore.Label.ResultReached)
                checkpoint(game)
            }
        }

        private fun visibilityChanged(active: Boolean) {
            if (state().active == active) return
            dispatch(Message.ActiveChanged(active))
            accumulatorSeconds = 0f
            if (!active) checkpoint()
        }

        private fun applyAction(
            result: ActionResult,
            checkpoint: Boolean = false,
            label: FruitMergeStore.Label? = null,
        ) {
            if (result.rejection != null || result.state == state().game) return
            replace(result.state, checkpoint)
            if (label != null) publish(label)
        }

        private fun publishMergeLabels(before: FruitMergeState, after: FruitMergeState) {
            if (before.score == after.score) return
            val previousIds = before.bodies.asSequence().map { body -> body.id }.toHashSet()
            after.bodies
                .asSequence()
                .filterNot { body -> body.id in previousIds }
                .sortedBy { body -> body.id }
                .forEach { body -> publish(FruitMergeStore.Label.MergeResolved(body.level)) }

            val survivingIds = after.bodies.asSequence().map { body -> body.id }.toHashSet()
            val removedMelons = before.bodies.count { body ->
                body.level == FruitLevel.MELON && body.id !in survivingIds
            }
            repeat(removedMelons / 2) {
                publish(FruitMergeStore.Label.MergeResolved(FruitLevel.MELON))
            }
        }

        private fun replace(game: FruitMergeState, checkpoint: Boolean = false) {
            if (game == state().game) return
            dispatch(Message.GameChanged(game))
            if (checkpoint) checkpoint(game)
        }

        private fun checkpoint(game: FruitMergeState = state().game) {
            if (!state().initialized) return
            scope.launch { persistence.checkpoint(game) }
        }
    }

    private companion object {
        const val FIXED_STEP_SECONDS: Float = 1f / 60f
        const val MAX_STEPS_PER_FRAME: Int = 3
        const val STEP_EPSILON: Float = 0.000_001f
    }
}
