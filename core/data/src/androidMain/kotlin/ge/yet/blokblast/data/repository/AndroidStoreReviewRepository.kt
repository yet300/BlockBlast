package ge.yet.blokblast.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.data.platform.ActivityProvider
import ge.yet.blokblast.data.platform.PlayStoreInAppReviewManager
import ge.yet.blokblast.domain.repository.ReviewCode
import ge.yet.blokblast.domain.repository.StoreReviewRepository
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