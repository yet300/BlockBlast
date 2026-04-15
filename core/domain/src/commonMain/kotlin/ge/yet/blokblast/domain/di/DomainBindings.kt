package ge.yet.blokblast.domain.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.domain.engine.ShapeGenerator
import ge.yet.blokblast.domain.engine.WeightedShapeGenerator

/**
 * Domain-layer bindings contributed to the app-wide [AppScope] graph.
 *
 * Only public interface ↔ internal implementation bindings live here; the rest
 * of the domain classes ([ge.yet.blokblast.domain.engine.GameEngine], [ge.yet.blokblast.domain.engine.ScoreCalculator]) use `@Inject` and are
 */
@ContributesTo(AppScope::class)
@BindingContainer
object DomainBindings {

    @Provides
    @SingleIn(AppScope::class)
    internal fun provideShapeGenerator(impl: WeightedShapeGenerator): ShapeGenerator = impl
}
