package ge.yet.game.feature.catalog.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import ge.yet.game.feature.catalog.CatalogComponent
import ge.yet.game.feature.catalog.DefaultCatalogComponentFactory

@ContributesTo(AppScope::class)
@BindingContainer
abstract class CatalogBindings {
    @Binds
    internal abstract val DefaultCatalogComponentFactory.bindCatalogComponentFactory:
        CatalogComponent.Factory
}
