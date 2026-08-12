package ge.yet.game.telemetry.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.domain.repository.CrashlyticsRepository
import ge.yet.game.telemetry.repository.AnalyticRepositoryImpl
import ge.yet.game.telemetry.repository.CrashlyticsRepositoryImpl

/**
 * Firebase-backed telemetry bindings, contributed to [AppScope].
 *
 * Lives in its own module so [ge.yet.blokblast.data] (and its tests) can build
 * and link on iOS without needing Firebase native frameworks at link time.
 */
@ContributesTo(AppScope::class)
@BindingContainer
abstract class TelemetryBindings {

    @Binds
    internal abstract val CrashlyticsRepositoryImpl.bindCrashlyticsRepository: CrashlyticsRepository

    @Binds
    internal abstract val AnalyticRepositoryImpl.bindAnalyticRepository: AnalyticRepository
}
