package ge.yet.game.domain.repository

/**
 * Abstracts audio file delivery so platform sound players in `core:data`
 * don't need a Compose dependency to read assets.
 *
 * The active game supplies the implementation and owns the actual assets.
 * Implementations may use Compose Resources to read bytes portably on iOS.
 */
interface AudioFileProvider {
    /** Returns the platform asset path for the named file. */
    fun path(filename: String): String

    /** Returns the raw bytes of the named audio file, or null if not found. */
    suspend fun bytes(filename: String): ByteArray?
}
