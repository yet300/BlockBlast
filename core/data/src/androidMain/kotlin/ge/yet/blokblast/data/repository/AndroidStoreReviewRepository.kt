package ge.yet.blokblast.data.repository

import com.mikhailovskii.inappreview.googlePlay.GooglePlayInAppReviewInitParams
import com.mikhailovskii.inappreview.googlePlay.GooglePlayInAppReviewManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.data.platform.ActivityProvider
import ge.yet.blokblast.domain.repository.ReviewCode
import ge.yet.blokblast.domain.repository.StoreReviewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.mikhailovskii.inappreview.ReviewCode as LibraryReviewCode

@SingleIn(AppScope::class)
@Inject
internal class AndroidStoreReviewRepository(
    private val activityProvider: ActivityProvider,
) : StoreReviewRepository {

    override fun init() {
        delegate().init()
    }

    override fun requestInAppReview(): Flow<ReviewCode> =
        delegate().requestInAppReview().map { it.toDomain() }

    override fun requestInMarketReview(): Flow<ReviewCode> =
        delegate().requestInMarketReview().map { it.toDomain() }

    private fun delegate() = GooglePlayInAppReviewManager(
        GooglePlayInAppReviewInitParams(activityProvider.current()),
    )

    private fun LibraryReviewCode.toDomain(): ReviewCode =
        runCatching { ReviewCode.valueOf(name) }.getOrDefault(ReviewCode.INTERNAL_ERROR)
}
