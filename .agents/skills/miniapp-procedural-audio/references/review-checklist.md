# Procedural audio review checklist

## Ownership and API

- Program is immutable, game-owned and outside Compose UI.
- Only public `ge.yet.game.miniapp.audio` and preset APIs are imported; no `internal`, platform player, engine, host or Settings dependency.
- `MiniAppAudio` is session-injected; teardown is not reimplemented.
- Typed control/SFX names match declarations and use snake case.

## Originality and product behavior

- Music, rhythm, voices and SFX are original or use repository presets.
- No Klang/Strudel/demo/commercial composition was transcribed.
- Global user Music/SFX preferences and visibility suppression are respected.
- Rejections are handled without retry loops.

## Sound and performance

- Presets were tried before game-owned synthesis.
- Seeds are explicit and deterministic.
- Gains leave headroom during overlapping music/SFX.
- Rapid SFX, controls, stereo and adaptive extremes were checked.
- Program stays below documented mobile budgets.
- Retro bit depth is an effect, not an output-format change.

## Verification

- Declaration compiles in `commonTest`.
- New voice/preset has deterministic acoustic render assertions where warranted.
- Module `allTests`, Android compile and iOS simulator compile pass.
- Counter remains a compact adaptive example; Block Blast remains a preset-composition example without host/platform imports.
