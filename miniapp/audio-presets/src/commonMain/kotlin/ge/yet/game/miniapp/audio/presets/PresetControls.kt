package ge.yet.game.miniapp.audio.presets

import ge.yet.game.miniapp.audio.AudioParameter

internal fun Float.requirePresetGain(): Float {
    require(isFinite() && this in 0f..1f) { "Preset gain must be finite and in 0..1" }
    return this
}

internal fun Float.requirePresetUnit(label: String): Float {
    require(isFinite() && this in 0f..1f) { "$label must be finite and in 0..1" }
    return this
}

internal fun AudioParameter.requirePresetUnit(label: String): AudioParameter {
    require(outputRange.start >= 0f && outputRange.endInclusive <= 1f) {
        "$label output range must stay in 0..1"
    }
    return this
}
