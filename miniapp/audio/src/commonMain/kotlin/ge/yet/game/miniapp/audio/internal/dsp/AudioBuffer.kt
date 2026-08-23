package ge.yet.game.miniapp.audio.internal.dsp

internal class StereoAudioBuffer(val capacity: Int) {
    val left = FloatArray(capacity)
    val right = FloatArray(capacity)

    init {
        require(capacity > 0)
    }

    fun clear(frameCount: Int) {
        require(frameCount in 0..capacity)
        left.fill(0f, 0, frameCount)
        right.fill(0f, 0, frameCount)
    }

    fun sanitize(frameCount: Int) {
        require(frameCount in 0..capacity)
        for (index in 0 until frameCount) {
            if (!left[index].isFinite()) left[index] = 0f
            if (!right[index].isFinite()) right[index] = 0f
        }
    }
}
