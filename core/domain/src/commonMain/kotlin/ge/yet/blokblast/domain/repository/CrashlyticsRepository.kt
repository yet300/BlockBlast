package ge.yet.blokblast.domain.repository


interface CrashlyticsRepository {

    fun setUserID(id: String)

    fun clearUserID()

    fun setCustomValue(key: String, value: Any)

    fun logException(throwable: Throwable)

    fun logMessage(message: String)
}