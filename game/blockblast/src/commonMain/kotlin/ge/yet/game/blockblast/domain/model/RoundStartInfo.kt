package ge.yet.game.blockblast.domain.model

/** Diagnostic information about a freshly created round. Not persisted. */
internal enum class RoundLayoutSource { EMPTY, STARTER }

internal data class RoundStartInfo(
    val layoutSource: RoundLayoutSource,
    val starterTemplateId: Int? = null,
    val quarterTurns: Int? = null,
    val reflectedHorizontally: Boolean? = null,
)
