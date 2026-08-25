package ge.yet.game.twentyfortyeight.persistence

import ge.yet.game.miniapp.api.MiniAppSnapshotSpec
import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.testkit.MutableMiniAppStorage

internal class RecordingMiniAppStorage private constructor(
    private val delegate: MutableMiniAppStorage,
    var failingRead: String?,
    var failingWrite: String?,
) : MiniAppStorage by delegate {
    constructor(
        initialSnapshots: Map<String, Any> = emptyMap(),
        failingRead: String? = null,
        failingWrite: String? = null,
    ) : this(MutableMiniAppStorage(initialSnapshots), failingRead, failingWrite)

    val reads = mutableListOf<String>()
    val writes = mutableListOf<String>()

    override suspend fun <T> readSnapshot(localName: String, spec: MiniAppSnapshotSpec<T>): T? {
        reads += localName
        if (localName == failingRead) throw RecordingStorageException(localName)
        return delegate.readSnapshot(localName, spec)
    }

    override suspend fun <T> writeSnapshot(
        localName: String,
        value: T,
        spec: MiniAppSnapshotSpec<T>,
    ) {
        writes += localName
        if (localName == failingWrite) throw RecordingStorageException(localName)
        delegate.writeSnapshot(localName, value, spec)
    }

    suspend fun <T> snapshot(localName: String, spec: MiniAppSnapshotSpec<T>): T? =
        delegate.readSnapshot(localName, spec)
}

internal class RecordingStorageException(localName: String) : RuntimeException(localName)
