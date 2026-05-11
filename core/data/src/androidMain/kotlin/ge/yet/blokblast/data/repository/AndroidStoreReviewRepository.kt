package ge.yet.blokblast.data.repository

import android.app.Activity
import com.mikhailovskii.inappreview.googlePlay.GooglePlayInAppReviewInitParams
import com.mikhailovskii.inappreview.googlePlay.GooglePlayInAppReviewManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.data.platform.ActivityProvider
import ge.yet.blokblast.domain.repository.ReviewCode
import ge.yet.blokblast.domain.repository.StoreReviewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.mikhailovskii.inappreview.ReviewCode as LibraryReviewCode

@SingleIn(AppScope::class)
@Inject
internal class AndroidStoreReviewRepository(
    private val activityProvider: ActivityProvider,
) : StoreReviewRepository {

    // Cache the delegate keyed by Activity identity so successive request*()
    // calls reuse the same manager instance bound to the foreground Activity.
    private var cachedDelegate: GooglePlayInAppReviewManager? = null
    private var cachedActivity: Activity? = null

    override fun requestInAppReview(): Flow<ReviewCode> {
        val manager = delegate() ?: return flowOf(ReviewCode.INTERNAL_ERROR)
        return manager.requestInAppReview().map { it.toDomain() }
    }

    override fun requestInMarketReview(): Flow<ReviewCode> {
        val manager = delegate() ?: return flowOf(ReviewCode.INTERNAL_ERROR)
        return manager.requestInMarketReview().map { it.toDomain() }
    }

    private fun delegate(): GooglePlayInAppReviewManager? {
        val act = activityProvider.current() ?: return null // Возвращаем null если нет Activity
        val cached = cachedDelegate
        if (cached != null && cachedActivity === act) return cached
        return GooglePlayInAppReviewManager(GooglePlayInAppReviewInitParams(act))
            .also {
                cachedDelegate = it
                cachedActivity = act
            }
    }

    private fun LibraryReviewCode.toDomain(): ReviewCode =
        runCatching { ReviewCode.valueOf(name) }.getOrDefault(ReviewCode.INTERNAL_ERROR)
}
