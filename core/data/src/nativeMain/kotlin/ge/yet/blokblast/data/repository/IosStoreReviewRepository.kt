package ge.yet.blokblast.data.repository

import com.app.common.config.AppConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.data.platform.AppStoreInAppReviewInitParams
import ge.yet.blokblast.data.platform.AppStoreInAppReviewManager
import ge.yet.blokblast.domain.repository.ReviewCode
import ge.yet.blokblast.domain.repository.StoreReviewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ge.yet.blokblast.domain.repository.ReviewCode as LibraryReviewCode

@SingleIn(AppScope::class)
@Inject
internal class IosStoreReviewRepository : StoreReviewRepository {

    private val delegate by lazy {
        AppStoreInAppReviewManager(
            AppStoreInAppReviewInitParams(appStoreId = AppConfig.IOS_APP_STORE_ID),
        )
    }

    override fun requestInAppReview(): Flow<ReviewCode> =
        delegate.requestInAppReview().map { it.toDomain() }

    override fun requestInMarketReview(): Flow<ReviewCode> =
        delegate.requestInMarketReview().map { it.toDomain() }

    private fun LibraryReviewCode.toDomain(): ReviewCode =
        runCatching { ReviewCode.valueOf(name) }.getOrDefault(ReviewCode.INTERNAL_ERROR)
}
