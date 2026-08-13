package ge.yet.game.feature.review.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import ge.yet.game.feature.review.AppReviewComponent
import ge.yet.game.feature.review.policy.AppReviewPolicy
import ge.yet.game.feature.review.DefaultAppReviewComponentFactory
import ge.yet.game.feature.review.policy.DefaultAppReviewPolicy

@ContributesTo(AppScope::class)
@BindingContainer
abstract class ReviewBindings {
    @Binds
    internal abstract val DefaultAppReviewComponentFactory.bindAppReviewComponentFactory:
        AppReviewComponent.Factory

    @Binds
    internal abstract val DefaultAppReviewPolicy.bindAppReviewPolicy: AppReviewPolicy
}
