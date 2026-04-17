package ge.yet3.blokblast.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocals that carry the user's Settings preferences down the tree
 * without prop-drilling. Provided once in [App] from [RootComponent] flows.
 *
 * Default values are permissive (both enabled) so previews and tests work
 * without needing a provider.
 */
val LocalVibrationEnabled = compositionLocalOf { true }
val LocalSoundEnabled = compositionLocalOf { true }
