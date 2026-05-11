package ge.yet.blokblast.data.platform

import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
import ge.yet.blokblast.domain.repository.ReviewCode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal class PlayStoreInAppReviewManager(
    private val activityProvider: ActivityProvider,
) {
    /**
     * Запускает сценарий внутриигрового обзора (In-App Review).
     */
    fun requestInAppReview(): Flow<ReviewCode> = callbackFlow {
        val activity = activityProvider.current()
        if (activity == null) {
            trySend(ReviewCode.INTERNAL_ERROR)
            close()
            return@callbackFlow
        }

        val manager = ReviewManagerFactory.create(activity)
        val request = manager.requestReviewFlow()

        request.addOnCompleteListener { task ->
            val currentActivity = activityProvider.current()
            if (currentActivity == null) {
                trySend(ReviewCode.INTERNAL_ERROR)
                close()
                return@addOnCompleteListener
            }

            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(currentActivity, reviewInfo)
                flow.addOnCompleteListener { _ ->
                    // Поток завершен. API не сообщает, оставил ли пользователь отзыв.
                    trySend(ReviewCode.NO_ERROR)
                    close()
                }
            } else {
                val exception = task.exception
                val reviewCode = if (exception is ReviewException) {
                    mapErrorCode(exception.errorCode)
                } else {
                    ReviewCode.INTERNAL_ERROR
                }
                trySend(reviewCode)
                close()
            }
        }

        awaitClose { }
    }

    /**
     * Открывает страницу приложения в Play Store для ручного отзыва.
     */
    fun requestInMarketReview(): Flow<ReviewCode> = callbackFlow {
        val activity = activityProvider.current()
        if (activity == null) {
            trySend(ReviewCode.INTERNAL_ERROR)
            close()
            return@callbackFlow
        }

        try {
            val packageName = activity.packageName
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Проверка наличия Play Store, иначе открываем в браузере
            if (intent.resolveActivity(activity.packageManager) == null) {
                intent.data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            }

            activity.startActivity(intent)
            trySend(ReviewCode.NO_ERROR)
        } catch (e: Exception) {
            trySend(ReviewCode.STORE_NOT_FOUND)
        }
        close()
        awaitClose { }
    }

    private fun mapErrorCode(errorCode: Int): ReviewCode {
        return when (errorCode) {
            ReviewErrorCode.NO_ERROR -> ReviewCode.NO_ERROR
            ReviewErrorCode.PLAY_STORE_NOT_FOUND -> ReviewCode.STORE_NOT_FOUND
            ReviewErrorCode.INVALID_REQUEST -> ReviewCode.INVALID_REQUEST
            ReviewErrorCode.INTERNAL_ERROR -> ReviewCode.INTERNAL_ERROR
            else -> ReviewCode.INTERNAL_ERROR
        }
    }
}