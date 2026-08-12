package ge.yet.game.domain.model

/** Diagnostic information about a freshly created round. Not persisted. */
enum class RoundLayoutSource { EMPTY, STARTER }

data class RoundStartInfo(
    val layoutSource: RoundLayoutSource,
    val starterTemplateId: Int? = null,
    val quarterTurns: Int? = null,
    val reflectedHorizontally: Boolean? = null,
)
