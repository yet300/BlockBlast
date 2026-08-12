package ge.yet.game.domain.repository


interface AnalyticRepository {

    fun logEvent(
        eventName: String,
        params: Map<String, Any>?
    )

    fun deleteData()
}