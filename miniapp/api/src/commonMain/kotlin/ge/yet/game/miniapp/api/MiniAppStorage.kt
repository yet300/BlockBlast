package ge.yet.game.miniapp.api

import kotlinx.coroutines.flow.Flow

interface MiniAppStorage {
    suspend fun getBoolean(localName: String, defaultValue: Boolean = false): Boolean
    suspend fun putBoolean(localName: String, value: Boolean)
    fun observeBoolean(localName: String, defaultValue: Boolean = false): Flow<Boolean>

    suspend fun getInt(localName: String, defaultValue: Int = 0): Int
    suspend fun putInt(localName: String, value: Int)
    fun observeInt(localName: String, defaultValue: Int = 0): Flow<Int>

    suspend fun getLong(localName: String, defaultValue: Long = 0L): Long
    suspend fun putLong(localName: String, value: Long)
    fun observeLong(localName: String, defaultValue: Long = 0L): Flow<Long>

    suspend fun getFloat(localName: String, defaultValue: Float = 0f): Float
    suspend fun putFloat(localName: String, value: Float)
    fun observeFloat(localName: String, defaultValue: Float = 0f): Flow<Float>

    suspend fun getDouble(localName: String, defaultValue: Double = 0.0): Double
    suspend fun putDouble(localName: String, value: Double)
    fun observeDouble(localName: String, defaultValue: Double = 0.0): Flow<Double>

    suspend fun getString(localName: String, defaultValue: String = ""): String
    suspend fun putString(localName: String, value: String)
    fun observeString(localName: String, defaultValue: String = ""): Flow<String>

    suspend fun remove(localName: String)

    suspend fun <T> readSnapshot(
        localName: String,
        spec: MiniAppSnapshotSpec<T>,
    ): T?

    suspend fun <T> writeSnapshot(
        localName: String,
        value: T,
        spec: MiniAppSnapshotSpec<T>,
    )
}

fun interface MiniAppStorageProvider {
    fun storageFor(id: MiniAppId): MiniAppStorage
}
