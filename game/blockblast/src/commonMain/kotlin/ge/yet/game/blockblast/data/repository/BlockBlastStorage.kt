package ge.yet.game.blockblast.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.api.MiniAppStorageProvider

internal val BLOCK_BLAST_ID = MiniAppId("game.blockblast")

@Inject
@SingleIn(AppScope::class)
internal class BlockBlastStorage(
    provider: MiniAppStorageProvider,
) : MiniAppStorage by provider.storageFor(BLOCK_BLAST_ID) {
    constructor(storage: MiniAppStorage) : this(MiniAppStorageProvider { storage })
}
