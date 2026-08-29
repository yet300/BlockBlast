    # Fruitmerge MiniApp

    Read [the AI contributor protocol](../../docs/miniapp/AI_CONTRIBUTOR_PROTOCOL.md)
    before making changes. The human workflow is documented in
    [the MiniApp contributor guide](../../docs/CONTRIBUTING_MINIAPP.md).

    Use `MiniAppId("game.fruitmerge").storageKey(localName)` for every new persistent key. Never copy another plugin's key prefix.
    This project is discovered on the next Gradle invocation, but is not shipped until a maintainer adds it to the production allowlist.
    Verify it with `./gradlew :game:fruitmerge:verifyMiniApp`.
    

This profile includes a pure state/action/engine seam. Keep rules in `FruitmergeGameEngine`, keep state immutable, and keep UI side-effect free. It is a small starting point, not a universal game engine.

