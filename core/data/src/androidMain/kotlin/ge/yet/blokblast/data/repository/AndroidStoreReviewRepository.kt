package ge.yet.blokblast.data.repository

import android.app.Activity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.data.platform.GooglePlayInAppReviewInitParams
import ge.yet.blokblast.data.platform.GooglePlayInAppReviewManager
import ge.yet.blokblast.data.platform.ActivityProvider
import ge.yet.blokblast.domain.repository.ReviewCode
import ge.yet.blokblast.domain.repository.StoreReviewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ge.yet.blokblast.domain.repository.ReviewCode as LibraryReviewCode

@SingleIn(AppScope::class)
@Inject
internal class AndroidStoreReviewRepository(
    private val activityProvider: ActivityProvider,
) : StoreReviewRepository {

    // Cache the delegate keyed by Activity identity so init() and subsequent
    // request*() calls target the same manager instance. Allocating a fresh
    // manager per call (the previous behaviour) meant init() seeded a different
    // instance than the one request*() used, so the review flow could stall.
    private var cachedDelegate: GooglePlayInAppReviewManager? = null
    private var cachedActivity: Activity? = null

    override fun init() {
        delegate().init()
    }

    override fun requestInAppReview(): Flow<ReviewCode> =
        delegate().requestInAppReview().map { it.toDomain() }

    override fun requestInMarketReview(): Flow<ReviewCode> =
        delegate().requestInMarketReview().map { it.toDomain() }

    private fun delegate(): GooglePlayInAppReviewManager {
        val act = activityProvider.current()
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
