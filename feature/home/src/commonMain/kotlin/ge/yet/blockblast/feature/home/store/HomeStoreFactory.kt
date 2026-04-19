package ge.yet.blockblast.feature.home.store

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import dev.zacsweers.metro.Inject
import ge.yet.blokblast.domain.repository.GameSaveRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.launch

@Inject
internal class HomeStoreFactory(
    private val storeFactory: StoreFactory,
    private val saveRepository: GameSaveRepository,
    private val settings: SettingsRepository,
) {
    fun create(): HomeStore =
        object :
            HomeStore,
            Store<HomeStore.Intent, HomeStore.State, Nothing> by storeFactory.create(
                name = "HomeStore",
                initialState = HomeStore.State(),
                executorFactory = coroutineExecutorFactory<HomeStore.Intent, Nothing, HomeStore.State, HomeStore.Msg, Nothing> {
                    onIntent<HomeStore.Intent.Refresh> {
                        launch {
                            val saved = saveRepository.load()
                            // Prefer the persisted settings best score — it
                            // survives clears of the in-memory save and is the
                            // true lifetime peak.
                            val persistedBest = settings.bestScore.value
                            val bestScore = maxOf(
                                persistedBest,
                                saved?.bestScore ?: 0L,
                            )
                            dispatch(
                                HomeStore.Msg.Loaded(
                                    bestScore = bestScore,
                                    hasSavedGame = saved != null && !saved.isGameOver,
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
