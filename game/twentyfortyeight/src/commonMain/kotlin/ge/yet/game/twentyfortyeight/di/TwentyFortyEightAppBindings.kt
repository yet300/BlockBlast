package ge.yet.game.twentyfortyeight.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.game.twentyfortyeight.diagnostics.CrashlyticsTwentyFortyEightDiagnostics
import ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightDiagnostics
import ge.yet.game.twentyfortyeight.engine.MoveEngine
import ge.yet.game.twentyfortyeight.engine.SpawnPolicy
import ge.yet.game.twentyfortyeight.store.NewGameSeedSource
import kotlin.random.Random

@ContributesTo(AppScope::class)
@BindingContainer
abstract class TwentyFortyEightAppBindings {
    @Binds
    internal abstract val CrashlyticsTwentyFortyEightDiagnostics.bindDiagnostics:
        TwentyFortyEightDiagnostics

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        internal fun provideSpawnPolicy(): SpawnPolicy = SpawnPolicy()

        @Provides
        @SingleIn(AppScope::class)
        internal fun provideMoveEngine(spawnPolicy: SpawnPolicy): MoveEngine = MoveEngine(spawnPolicy)

        @Provides
        @SingleIn(AppScope::class)
        internal fun provideNewGameSeedSource(): NewGameSeedSource =
            NewGameSeedSource { Random.Default.nextLong() }
    }
}
