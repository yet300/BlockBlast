package ge.yet.game.miniapp.compose

interface MiniAppPlugin {

    val manifest: MiniAppManifest

    fun createSession(context: MiniAppSessionContext): MiniAppSession
}
