package ge.yet.game.miniapp.testkit

import ge.yet.game.miniapp.api.MiniAppSnapshotSpec
import ge.yet.game.miniapp.api.MiniAppStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

object NoopMiniAppStorage : MiniAppStorage {
    override suspend fun getBoolean(localName: String, defaultValue: Boolean): Boolean = defaultValue
    override suspend fun putBoolean(localName: String, value: Boolean) = Unit
    override fun observeBoolean(localName: String, defaultValue: Boolean): Flow<Boolean> =
        flowOf(defaultValue)

    override suspend fun getInt(localName: String, defaultValue: Int): Int = defaultValue
    override suspend fun putInt(localName: String, value: Int) = Unit
    override fun observeInt(localName: String, defaultValue: Int): Flow<Int> = flowOf(defaultValue)

    override suspend fun getLong(localName: String, defaultValue: Long): Long = defaultValue
    override suspend fun putLong(localName: String, value: Long) = Unit
    override fun observeLong(localName: String, defaultValue: Long): Flow<Long> = flowOf(defaultValue)

    override suspend fun getFloat(localName: String, defaultValue: Float): Float = defaultValue
    override suspend fun putFloat(localName: String, value: Float) = Unit
    override fun observeFloat(localName: String, defaultValue: Float): Flow<Float> = flowOf(defaultValue)

    override suspend fun getDouble(localName: String, defaultValue: Double): Double = defaultValue
    override suspend fun putDouble(localName: String, value: Double) = Unit
    override fun observeDouble(localName: String, defaultValue: Double): Flow<Double> = flowOf(defaultValue)

    override suspend fun getString(localName: String, defaultValue: String): String = defaultValue
    override suspend fun putString(localName: String, value: String) = Unit
    override fun observeString(localName: String, defaultValue: String): Flow<String> = flowOf(defaultValue)

    override suspend fun remove(localName: String) = Unit

    override suspend fun <T> readSnapshot(
        localName: String,
        spec: MiniAppSnapshotSpec<T>,
    ): T? = null

    override suspend fun <T> writeSnapshot(
        localName: String,
        value: T,
        spec: MiniAppSnapshotSpec<T>,
    ) = Unit
}
