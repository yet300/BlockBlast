package ge.yet3.blokblast.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocals that carry the user's Settings preferences down the tree
 * without prop-drilling. Provided once in [App] from [RootComponent] flows.
 *
 * Default values are permissive (both enabled) so previews and tests work
 * without needing a provider.
 */
// staticCompositionLocalOf: these flip rarely; avoiding the per-read observer cost
// of compositionLocalOf is worth the full-subtree recomposition on flip.
val LocalVibrationEnabled = staticCompositionLocalOf { true }
val LocalSoundEnabled = staticCompositionLocalOf { true }

/** True once the user has finished or skipped the first-launch tutorial. */
val LocalTutorialSeen = staticCompositionLocalOf { true }

/** Callback the tutorial overlay invokes to mark itself as seen. */
val LocalOnTutorialSeen = staticCompositionLocalOf<() -> Unit> { {} }
