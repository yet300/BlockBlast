package ge.yet.blokblast.data.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.app.common.config.AppConfig.ANDROID_PACKAGE_NAME
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManagerFactory
import ge.yet.blokblast.data.internal.InAppReviewDelegate
import ge.yet.blokblast.domain.repository.ReviewCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

class GooglePlayInAppReviewInitParams(val activity: Activity)

class GooglePlayInAppReviewManager(private val params: GooglePlayInAppReviewInitParams) :
    InAppReviewDelegate {

    override fun requestInAppReview(): Flow<ReviewCode> = flow {
        val activity = params.activity
        val manager = ReviewManagerFactory.create(activity)
        val reviewInfo = manager.requestReviewFlow().await()
        manager.launchReviewFlow(activity, reviewInfo).await()
        emit(ReviewCode.NO_ERROR)
    }.catch { e ->
        if (e is ReviewException) {
            emit(ReviewCode.fromCode(e.errorCode))
        } else {
            emit(ReviewCode.INTERNAL_ERROR)
        }
    }

    override fun requestInMarketReview() = flow {
        params.activity.openMarket(
            deeplink = "market://details?id=$ANDROID_PACKAGE_NAME",
            url = "https://play.google.com/store/apps/details?id=$ANDROID_PACKAGE_NAME",
        )
        emit(ReviewCode.NO_ERROR)
    }
}



fun Context.openMarket(deeplink: String, url: String) {

    val marketAppIntent = Intent(Intent.ACTION_VIEW, Uri.parse(deeplink)).apply {
        flags += Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
    }

    val marketInBrowserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    runCatching {
        startActivity(marketAppIntent)
    }.getOrElse {
        startActivity(marketInBrowserIntent)
    }
}
