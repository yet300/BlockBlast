package ge.yet.game.monetization.core

fun shouldShowInterstitial(
    adsAllowed: Boolean,
    isReady: Boolean,
): Boolean = adsAllowed && isReady

fun once(action: () -> Unit): () -> Unit {
    var invoked = false
    return {
        if (!invoked) {
            invoked = true
            action()
        }
    }
}
