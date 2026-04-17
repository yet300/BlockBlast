package ge.yet.blokblast.domain.repository

/**
 * Abstracts audio file delivery so platform sound players in `core:data`
 * don't need a Compose dependency to read assets.
 *
 * The implementation lives in `composeApp` and uses `Res.readBytes()`,
 * which is the only portable way to read `composeResources/files/` on iOS.
 */
interface AudioFileProvider {
    /** Returns the raw bytes of the named audio file, or null if not found. */
    suspend fun bytes(filename: String): ByteArray?
}
