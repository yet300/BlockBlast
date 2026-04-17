package ge.yet3.blokblast.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Bottom banner ad slot.
 *
 * - Android: renders an AdMob [com.google.android.gms.ads.AdView] inside a 50dp-tall
 *   box. The height is reserved even before the ad loads so layout never jumps.
 * - iOS / other: no-op (zero-size); the caller's layout collapses cleanly.
 */
@Composable
expect fun AdBanner(modifier: Modifier = Modifier)
