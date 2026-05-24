package ge.yet.blokblast.domain.repository


interface AnalyticRepository {

    fun logEvent(
        eventName: String,
        params: Map<String, Any>?
    )

    fun deleteData()
}