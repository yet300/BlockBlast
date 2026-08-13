package ge.yet.game.feature.home.store

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import dev.zacsweers.metro.Inject
import ge.yet.game.domain.api.GameSaveApi
import ge.yet.game.domain.repository.AnalyticRepository
import kotlinx.coroutines.launch

@Inject
internal class HomeStoreFactory(
    private val storeFactory: StoreFactory,
    private val gameSaveApi: GameSaveApi,
    private val analytics: AnalyticRepository,
) {
    fun create(): HomeStore {
        val logger = HomeAnalyticsLogger(analytics)
        return object :
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
                            dispatch(
                                HomeStore.Msg.Loaded(
                                    hasSavedGame = gameSaveApi.hasSavedGame(),
                                )
                            )
                        }
                    }
                    onIntent<HomeStore.Intent.Refresh> {
                        launch {
                            val hasSavedGame = gameSaveApi.hasSavedGame()
                            logger.log("home_shown", hasSavedGame)
                            dispatch(
                                HomeStore.Msg.Loaded(
                                    hasSavedGame = hasSavedGame,
                                )
                            )
                        }
                    }
                },
                reducer = HomeReducer,
            ) {}
    }


    internal object HomeReducer : Reducer<HomeStore.State, HomeStore.Msg> {
        override fun HomeStore.State.reduce(msg: HomeStore.Msg): HomeStore.State = when (msg) {
            is HomeStore.Msg.Loaded -> copy(hasSavedGame = msg.hasSavedGame)
        }
    }

}
