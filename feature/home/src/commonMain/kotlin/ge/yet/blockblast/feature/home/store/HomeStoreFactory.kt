package ge.yet.blockblast.feature.home.store

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import dev.zacsweers.metro.Inject
import ge.yet.blokblast.domain.repository.AnalyticRepository
import ge.yet.blokblast.domain.repository.GameSaveRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.launch

@Inject
internal class HomeStoreFactory(
    private val storeFactory: StoreFactory,
    private val saveRepository: GameSaveRepository,
    private val settings: SettingsRepository,
    private val analytics: AnalyticRepository,
) {
    fun create(): HomeStore =
        object :
            HomeStore,
            Store<HomeStore.Intent, HomeStore.State, Nothing> by storeFactory.create(
                name = "HomeStore",
                initialState = HomeStore.State(),
                bootstrapper = SimpleBootstrapper(HomeStore.Action.LoadStarted),
                executorFactory = coroutineExecutorFactory<HomeStore.Intent, HomeStore.Action, HomeStore.State, HomeStore.Msg, Nothing> {
                    // Per the mvikotlin-code skill, the *initial* load is bootstrap
                    // work and must come from an Action, not an Intent. Intent.Refresh
                    // re-runs the same load when the screen is brought back to the
                    // foreground (see DefaultHomeComponent.lifecycle.doOnStart).
                    onAction<HomeStore.Action.LoadStarted> {
                        launch {
                            val saved = saveRepository.load()
                            val bestScore = maxOf(settings.bestScore.value, saved?.bestScore ?: 0L)
                            dispatch(
                                HomeStore.Msg.Loaded(
                                    bestScore = bestScore,
                                    hasSavedGame = saved != null && !saved.isGameOver && !saved.grid.isBoardEmpty(),
                                )
                            )
                        }
                    }
                    onIntent<HomeStore.Intent.Refresh> {
                        launch {
                            val saved = saveRepository.load()
                            val bestScore = maxOf(settings.bestScore.value, saved?.bestScore ?: 0L)
                            val hasSavedGame =
                                saved != null && !saved.isGameOver && !saved.grid.isBoardEmpty()
                            analytics.logEvent(
                                eventName = "home_shown",
                                params = mapOf(
                                    "best_score" to bestScore,
                                    "has_saved_game" to hasSavedGame,
                                ),
                            )
                            dispatch(
                                HomeStore.Msg.Loaded(
                                    bestScore = bestScore,
                                    hasSavedGame = hasSavedGame,
                                )
                            )
                        }
                    }
                },
                reducer = HomeReducer,
            ) {}


    internal object HomeReducer : Reducer<HomeStore.State, HomeStore.Msg> {
        override fun HomeStore.State.reduce(msg: HomeStore.Msg): HomeStore.State = when (msg) {
            is HomeStore.Msg.Loaded -> copy(bestScore = msg.bestScore, hasSavedGame = msg.hasSavedGame)
        }
    }

}
