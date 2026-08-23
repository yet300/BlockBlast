package ge.yet.game.miniapp.api

interface MiniAppDataResetter {
    suspend fun clear(miniAppIds: Set<MiniAppId>): MiniAppDataResetResult
}

sealed interface MiniAppDataResetResult {
    data object Success : MiniAppDataResetResult

    class PartialFailure(
        failedMiniAppIds: Set<MiniAppId>,
    ) : MiniAppDataResetResult {
        val failedMiniAppIds: Set<MiniAppId> = failedMiniAppIds.toSet()

        init {
            require(this.failedMiniAppIds.isNotEmpty()) {
                "A partial mini-app data reset failure must contain at least one MiniApp ID"
            }
            this.failedMiniAppIds.forEach(MiniAppId::requireValid)
        }
    }
}

class MiniAppLegacyStorageKeys(
    val miniAppId: MiniAppId,
    localToPhysicalKeys: Map<String, String>,
) {
    val localToPhysicalKeys: Map<String, String> = localToPhysicalKeys.toMap()

    init {
        miniAppId.requireValid()
        this.localToPhysicalKeys.forEach { (localName, physicalKey) ->
            requireValidMiniAppStorageLocalName(localName)
            require(physicalKey.isNotBlank()) { "A legacy mini-app storage key must not be blank" }
        }
        require(this.localToPhysicalKeys.values.toSet().size == this.localToPhysicalKeys.size) {
            "Legacy mini-app storage keys must map to distinct physical keys"
        }
    }
}

interface MiniAppAdditionalDataCleaner {
    val miniAppId: MiniAppId

    suspend fun clear()
}
