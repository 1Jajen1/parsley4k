package parsley.stack

class ArrayStack {
    private var content = arrayOfNulls<Any?>(ARR_SIZE)
    private var offset = 0

    fun push(el: Any?): Unit {
        content[offset++] = el
        if (offset == content.size) content = content.copyOf(content.size * 2)
    }

    fun pop(): Any? = content[--offset]

    fun peek(): Any? = content[offset - 1]

    fun exchange(a: Any?) {
        content[offset - 1] = a
    }

    fun drop() {
        offset--
    }

    internal fun setOffset(off: Int): Unit {
        offset = off
    }

    fun isEmpty(): Boolean = offset == 0
    fun isNotEmpty(): Boolean = offset != 0
    fun size(): Int = offset
    fun clear() {
        offset = 0
    }

    internal inline fun forEach(f: (Any?) -> Unit) {
        for (ind in 0 until offset) {
            f(content[ind])
        }
    }

    companion object {
        const val ARR_SIZE = 8
    }
}

