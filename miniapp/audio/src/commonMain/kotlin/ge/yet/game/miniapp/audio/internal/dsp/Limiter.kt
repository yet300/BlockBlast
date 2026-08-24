package ge.yet.game.miniapp.audio.internal.dsp

internal fun limitStereo(
    left: FloatArray,
    right: FloatArray,
    frameCount: Int,
    ceiling: Float = 0.98f,
    offset: Int = 0,
) {
    require(ceiling.isFinite() && ceiling > 0f && ceiling <= 1f)
    require(frameCount >= 0 && offset >= 0 && offset + frameCount <= left.size && offset + frameCount <= right.size)
    for (frame in 0 until frameCount) {
        val index = offset + frame
        left[index] = left[index].takeIf { it.isFinite() }?.coerceIn(-ceiling, ceiling) ?: 0f
        right[index] = right[index].takeIf { it.isFinite() }?.coerceIn(-ceiling, ceiling) ?: 0f
    }
}
