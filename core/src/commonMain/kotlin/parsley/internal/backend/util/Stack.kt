package parsley.internal.backend.util

import parsley.internal.unsafe

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

    fun setOffset(n: Int) {
        offset = n
    }

    fun clear() {
        offset = 0
        arr = IntArray(INITIAL_SIZE)
    }

    private fun grow(): Unit {
        arr = arr.copyOf(arr.size * 2)
    }

    companion object {
        private const val INITIAL_SIZE = 8
    }
}

internal class ArrayStack<A> {
    private var arr: Array<Any?> = arrayOfNulls(INITIAL_SIZE)
    private var offset = 0

    fun push(a: A): Unit {
        if (offset >= arr.size) grow()
        arr[offset++] = a
    }

    fun pop(): A {
        return arr[--offset].unsafe()
    }

    fun peek(): A = arr[offset - 1].unsafe()

    fun size(): Int = offset

    fun setOffset(n: Int) {
        offset = n
    }

    fun clear() {
        offset = 0
        arr = arrayOfNulls(INITIAL_SIZE)
    }

    private fun grow(): Unit {
        arr = arr.copyOf(arr.size * 2)
    }

    companion object {
        private const val INITIAL_SIZE = 8
    }
}
