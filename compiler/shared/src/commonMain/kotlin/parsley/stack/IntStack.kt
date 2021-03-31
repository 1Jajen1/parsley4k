package parsley.stack

class IntStack {
    private var content = IntArray(ARR_SIZE)
    private var offset = 0

    fun push(el: Int): Unit {
        content[offset++] = el
        if (offset == content.size) content = content.copyOf(content.size * 2)
    }

    fun pop(): Int = content[--offset]

    fun drop() {
        --offset
    }

    fun exchange(el: Int) {
        content[offset - 1] = el
    }

    internal fun setOffset(off: Int) {
        offset = off
    }

    fun isEmpty(): Boolean = offset == 0
    fun isNotEmpty(): Boolean = offset != 0
    fun size(): Int = offset

    companion object {
        const val ARR_SIZE = 8
    }
}