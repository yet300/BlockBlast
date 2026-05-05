package com.app.common.config

/**
 * Single source of truth for store-listing identifiers, AdMob unit IDs, and
 * related release-time constants. Swap these values (along with the
 * `com.google.android.gms.ads.APPLICATION_ID` meta-data in
 * `androidApp/src/main/AndroidManifest.xml` and `GADApplicationIdentifier`
 * in `iosApp/iosApp/Info.plist`) before publishing.
 *
 * Keeping everything here avoids hunting through multiple files when
 * updating production IDs.
 */
object AppConfig {

    // ── Store listings ─────────────────────────────────────────────────────
    /** Play Store package name — also used to build `market://` deeplinks. */
    const val ANDROID_PACKAGE_NAME: String = "ge.yet.blokblast"

    /** Numeric App Store ID (from App Store Connect). */
    // TODO: replace with the real App Store ID once provisioned.
    const val IOS_APP_STORE_ID: String = "6765924581"

    // ── AdMob ──────────────────────────────────────────────────────────────
    const val BANNER_UNIT_ID_ANDROID: String = "ca-app-pub-1829375480261561/8506212995"
    const val BANNER_UNIT_ID_IOS: String = "ca-app-pub-1829375480261561/9561597181"

    /** Interstitial shown after Game Over. TEST unit. */
    const val GAME_OVER_INTERSTITIAL_UNIT_ID_ANDROID: String =
        "ca-app-pub-1829375480261561/8963087579"
    const val GAME_OVER_INTERSTITIAL_UNIT_ID_IOS: String =
        "ca-app-pub-1829375480261561/5009161004"

    // ── In-app review ──────────────────────────────────────────────────────
    /**
     * Minimum score at which the OS-level in-app review prompt is requested
     * on a new personal best. The store SDK additionally throttles requests,
     * so crossing this threshold often will not spam the user.
     */
    const val REVIEW_MIN_SCORE: Int = 500

    /**
     * Additional score the user must beat their previous best by before the
     * in-app review prompt is requested again. Combined with [REVIEW_MIN_SCORE]
     * and [REVIEW_MAX_PROMPTS], this keeps the dialog from feeling pushy.
     */
    const val REVIEW_BEST_SCORE_DELTA: Long = 1000L

    /**
     * Hard lifetime cap on how many times the in-app review prompt may be
     * triggered for a given user. The OS SDK already throttles further, but
     * this guarantees we never ask more than this many times even on devices
     * where its quota has reset.
     */
    const val REVIEW_MAX_PROMPTS: Int = 2

    // ── Legal ──────────────────────────────────────────────────────────────
    /** Public privacy-policy URL linked from the Settings screen. */
    const val PRIVACY_POLICY_URL: String =
        "https://github.com/yet300/block_blast/blob/main/privacy_policy.md"

    // ── Drag & Drop ────────────────────────────────────────────────────────
    /**
     * Multiplier for drag speed. Values > 1.0 make the piece move faster than
     * the finger relative to the pickup point. 1.15 is a good balance for
     * reaching screen corners on large phones.
     */
    const val DRAG_SENSITIVITY: Float = 1.15f
}
