package ge.yet.game.miniapp.audio.presets

internal fun Float.requirePresetGain(): Float {
    require(isFinite() && this in 0f..1f) { "Preset gain must be finite and in 0..1" }
    return this
}
