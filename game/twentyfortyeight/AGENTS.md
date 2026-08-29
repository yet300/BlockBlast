# 2048 MiniApp Agent Guide

This module owns the allowlisted `game.twentyfortyeight` MiniApp in the
`ge.yet.game.twentyfortyeight` package. It is included in `:miniapp:bundle` by
the root `miniApps` declarations. Allowlisting controls compiled production
membership; a separate release decision controls store distribution.

Keep dependencies inward and limited to the `logica.miniapp` convention,
`:core:common`, `:core:domain`, `:core:uikit`, and the existing MVI bundle.
Do not depend on feature, application, native-ad, telemetry implementation,
platform-audio, raw Settings, or another game/sample module.

The pure engine owns rules, board transitions, deterministic RNG, score and
statistics. It performs no persistence, navigation, analytics, audio, host
callbacks, Compose work, or delays. UI receives immutable models and actions;
business mutation stays outside composables.

Every runtime session uses only the `MiniAppSessionContext.storage` and
`MiniAppSessionContext.audio` facades supplied to its retained child graph.
Persistent names are local snake-case names under the host-owned namespace;
never construct physical keys or import `MiniAppStorageProvider`. Audio uses
only the public MiniApp procedural API/presets and never native players.

Do not add haptics, platform imports, copied 2048 expression, borrowed assets,
music, patterns, seeds, palette, layout, or animation constants. Preserve the
clean-room record in `PROVENANCE.md` and the architecture in the approved 2048
design and implementation-plan documents.
