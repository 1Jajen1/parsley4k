package parsley.backend

import parsley.slice

internal class StringStackMachine<E> internal constructor(instr: Array<Instruction<Char, E>>) :
    AbstractStackMachine<Char, E>(instr) {

    internal var input: CharArray = charArrayOf()

    internal var warnBoxing = false
    override var acceptMoreInput: Boolean = true

    override fun hasMore(): Boolean = inputOffset < input.size
    override fun take(): Char = input[inputOffset].also { warnBoxing = true }
    override fun hasMore(n: Int): Boolean = inputOffset < input.size - (n - 1)
    override fun slice(start: Int, end: Int): Array<Char> =
        input.slice(start, end).toTypedArray().also { warnBoxing = true }

    fun takeP(): Char = input[inputOffset]
    fun takeP(start: Int, end: Int): CharArray = input.slice(start, end)

    fun feed(inp: CharArray): Unit {
        input += inp
    }
}


