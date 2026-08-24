package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.audio.AudioControlDeclaration
import ge.yet.game.miniapp.audio.AudioControlName

/** Fixed-capacity control lookup. Realtime DSP uses only allocation-free [get]. */
internal class RealtimeControlPositions(
    val capacity: Int,
) : Map<AudioControlName, Float> {
    private val names = arrayOfNulls<AudioControlName>(capacity)
    private val positions = FloatArray(capacity)

    override var size: Int = 0
        private set

    init {
        require(capacity > 0)
    }

    fun reset(declarations: List<AudioControlDeclaration>): Boolean {
        if (declarations.size > capacity) return false
        clear()
        for (index in declarations.indices) {
            val declaration = declarations[index]
            names[index] = declaration.name
            val width = declaration.range.endInclusive - declaration.range.start
            positions[index] = if (width == 0f) {
                0f
            } else {
                (declaration.default - declaration.range.start) / width
            }
        }
        size = declarations.size
        return true
    }

    fun set(name: AudioControlName, position: Float): Boolean {
        val index = indexOf(name)
        if (index < 0) return false
        positions[index] = position
        return true
    }

    fun clear() {
        for (index in 0 until size) names[index] = null
        size = 0
    }

    override fun get(key: AudioControlName): Float? {
        val index = indexOf(key)
        return if (index < 0) null else positions[index]
    }

    override fun containsKey(key: AudioControlName): Boolean = indexOf(key) >= 0

    override fun containsValue(value: Float): Boolean {
        for (index in 0 until size) if (positions[index] == value) return true
        return false
    }

    override fun isEmpty(): Boolean = size == 0

    override val entries: Set<Map.Entry<AudioControlName, Float>>
        get() = buildSet(size) {
            for (index in 0 until size) add(ControlEntry(requireNotNull(names[index]), positions[index]))
        }

    override val keys: Set<AudioControlName>
        get() = buildSet(size) {
            for (index in 0 until size) add(requireNotNull(names[index]))
        }

    override val values: Collection<Float>
        get() = List(size) { positions[it] }

    private fun indexOf(name: AudioControlName): Int {
        for (index in 0 until size) if (names[index] == name) return index
        return -1
    }

    private data class ControlEntry(
        override val key: AudioControlName,
        override val value: Float,
    ) : Map.Entry<AudioControlName, Float>
}
