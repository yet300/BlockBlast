package ge.yet.blokblast.data.internal

import ge.yet.blokblast.domain.repository.ReviewCode
import kotlinx.coroutines.flow.Flow

internal interface InAppReviewDelegate {
    fun requestInAppReview(): Flow<ReviewCode>
    fun requestInMarketReview(): Flow<ReviewCode>
}