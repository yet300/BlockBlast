package ge.yet.game.miniapp.testkit

import ge.yet.game.miniapp.api.MiniAppSnapshotSpec
import ge.yet.game.miniapp.api.MiniAppStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class MutableMiniAppStorage(
    initialValues: Map<String, Any> = emptyMap(),
) : MiniAppStorage {
    private val values = initialValues.toMutableMap()
    private val flows = mutableMapOf<String, MutableStateFlow<Any>>()
    private val defaults = mutableMapOf<String, Any>()

    override suspend fun getBoolean(localName: String, defaultValue: Boolean): Boolean =
        value(localName, defaultValue)

    override suspend fun putBoolean(localName: String, value: Boolean) = put(localName, value)

    override fun observeBoolean(localName: String, defaultValue: Boolean): Flow<Boolean> =
        typedFlow(localName, defaultValue)

    override suspend fun getInt(localName: String, defaultValue: Int): Int = value(localName, defaultValue)

    override suspend fun putInt(localName: String, value: Int) = put(localName, value)

    override fun observeInt(localName: String, defaultValue: Int): Flow<Int> =
        typedFlow(localName, defaultValue)

    override suspend fun getLong(localName: String, defaultValue: Long): Long = value(localName, defaultValue)

    override suspend fun putLong(localName: String, value: Long) = put(localName, value)

    override fun observeLong(localName: String, defaultValue: Long): Flow<Long> =
        typedFlow(localName, defaultValue)

    override suspend fun getFloat(localName: String, defaultValue: Float): Float = value(localName, defaultValue)

    override suspend fun putFloat(localName: String, value: Float) = put(localName, value)

    override fun observeFloat(localName: String, defaultValue: Float): Flow<Float> =
        typedFlow(localName, defaultValue)

    override suspend fun getDouble(localName: String, defaultValue: Double): Double = value(localName, defaultValue)

    override suspend fun putDouble(localName: String, value: Double) = put(localName, value)

    override fun observeDouble(localName: String, defaultValue: Double): Flow<Double> =
        typedFlow(localName, defaultValue)

    override suspend fun getString(localName: String, defaultValue: String): String =
        value(localName, defaultValue)

    override suspend fun putString(localName: String, value: String) = put(localName, value)

    override fun observeString(localName: String, defaultValue: String): Flow<String> =
        typedFlow(localName, defaultValue)

    override suspend fun remove(localName: String) {
        values.remove(localName)
        flows[localName]?.let { flow -> flow.value = defaults.getValue(localName) }
    }

    override suspend fun <T> readSnapshot(localName: String, spec: MiniAppSnapshotSpec<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return values[localName] as T?
    }

    override suspend fun <T> writeSnapshot(
        localName: String,
        value: T,
        spec: MiniAppSnapshotSpec<T>,
    ) {
        requireNotNull(value)
        put(localName, value)
    }

    suspend fun clear() {
        values.keys.toList().forEach { remove(it) }
    }

    private inline fun <reified T : Any> value(localName: String, defaultValue: T): T =
        values[localName] as? T ?: defaultValue

    private fun put(localName: String, value: Any) {
        values[localName] = value
        flows[localName]?.value = value
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> typedFlow(localName: String, defaultValue: T): Flow<T> =
        flows.getOrPut(localName) {
            defaults[localName] = defaultValue
            MutableStateFlow(values[localName] ?: defaultValue)
        } as Flow<T>
}
