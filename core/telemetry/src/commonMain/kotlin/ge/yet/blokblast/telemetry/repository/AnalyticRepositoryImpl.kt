package ge.yet.blokblast.telemetry.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.FirebaseAnalytics
import dev.gitlive.firebase.analytics.analytics
import ge.yet.blokblast.domain.repository.AnalyticRepository


@SingleIn(AppScope::class)
@Inject
internal class AnalyticRepositoryImpl : AnalyticRepository {
    private val analytics: FirebaseAnalytics = Firebase.analytics


    override fun logEvent(
        eventName: String,
        params: Map<String, Any>?
    ) {
        analytics.logEvent(eventName, params)
    }

    override fun deleteData() {
        analytics.resetAnalyticsData()
    }
}