package parsley.internal.backend.util

internal interface Stack<A> {
    fun push(a: A): Unit
    fun pop(): A
    fun size(): Int
    fun peek(): A
    fun clear(): Unit
}

internal class IntStack {
    private var arr: IntArray = IntArray(INITIAL_SIZE)
    private var offset = 0

    fun push(a: Int): Unit {
        if (offset >= arr.size) grow()
        arr[offset++] = a
    }

    fun pop(): Int {
        return arr[--offset]
    }

    fun peek(): Int = arr[offset - 1]

    fun size(): Int = offset

    fun clear() {
        offset = 0
        arr = IntArray(INITIAL_SIZE)
    }

    private fun grow(): Unit {
        arr = arr.copyOf(arr.size * 2)
    }

    companion object {
        private const val INITIAL_SIZE = 32
    }
}

internal class ArrayStack<A>: Stack<A> {
    private var arr: Array<Any?> = arrayOfNulls(INITIAL_SIZE)
    private var offset = 0

    override fun push(a: A): Unit {
        if (offset >= arr.size) grow()
        arr[offset++] = a
    }

    override fun pop(): A {
        return arr[--offset] as A
    }

    override fun peek(): A = arr[offset - 1] as A

    override fun size(): Int = offset

    override fun clear() {
        offset = 0
        arr = arrayOfNulls(INITIAL_SIZE)
    }

    private fun grow(): Unit {
        arr = arr.copyOf(arr.size * 2)
    }

    companion object {
        private const val INITIAL_SIZE = 32
    }
}
