package ge.yet.game.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.data.platform.ActivityProvider
import ge.yet.game.data.platform.PlayStoreInAppReviewManager
import ge.yet.game.domain.repository.ReviewCode
import ge.yet.game.domain.repository.StoreReviewRepository
import kotlinx.coroutines.flow.Flow

@SingleIn(AppScope::class)
@Inject
internal class AndroidStoreReviewRepository(
    private val activityProvider: ActivityProvider,
) : StoreReviewRepository {

    private val delegate by lazy {
        PlayStoreInAppReviewManager(activityProvider)
    }

    override fun requestInAppReview(): Flow<ReviewCode> =
        delegate.requestInAppReview()

    override fun requestInMarketReview(): Flow<ReviewCode> =
        delegate.requestInMarketReview()
}