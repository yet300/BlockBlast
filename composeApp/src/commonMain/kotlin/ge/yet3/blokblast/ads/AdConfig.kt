package ge.yet3.blokblast.ads

/**
 * Central registry of all AdMob unit IDs.
 *
 * All values are currently Google-provided TEST IDs. Swap every constant
 * in this file (and the APPLICATION_ID meta-data in AndroidManifest.xml)
 * for production IDs before release.
 */
object AdConfig {
    // TODO: replace with production App ID in AndroidManifest.xml
    //       (currently ca-app-pub-3940256099942544~3347511713 — TEST)

    /** Bottom banner on the Home screen. TEST unit. */
    // TODO: replace with production banner unit ID
    const val HOME_BANNER_UNIT_ID: String = "ca-app-pub-3940256099942544/6300978111"

    /** Interstitial shown after Game Over. TEST unit. */
    // TODO: replace with production interstitial unit ID
    const val GAME_OVER_INTERSTITIAL_UNIT_ID: String = "ca-app-pub-3940256099942544/1033173712"
}
