package ge.yet.blokblast.data.repository

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.domain.repository.CrashlyticsRepository


@SingleIn(AppScope::class)
@Inject
internal class CrashlyticsRepositoryImpl : CrashlyticsRepository {
    private val crashlytics by lazy { Firebase.crashlytics }

    override fun setUserID(id: String) {
        crashlytics.setUserId(id)
    }

    override fun clearUserID() {
        crashlytics.setUserId("")
    }

    override fun setCustomValue(key: String, value: Any) {
        when (value) {
            is String -> crashlytics.setCustomKey(key, value)
            is Boolean -> crashlytics.setCustomKey(key, value)
            is Int -> crashlytics.setCustomKey(key, value)
            is Long -> crashlytics.setCustomKey(key, value)
            is Float -> crashlytics.setCustomKey(key, value)
            is Double -> crashlytics.setCustomKey(key, value)
            else -> crashlytics.setCustomKey(key, value.toString())
        }
    }

    override fun logException(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }

    override fun logMessage(message: String) {
        crashlytics.log(message)
    }

}