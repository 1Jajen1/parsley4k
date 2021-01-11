package parsley.internal.backend.string

import parsley.internal.backend.Instruction
import parsley.internal.backend.StackMachine

internal class StringStackMachine<E>(
    instr: Array<Instruction<Char, E>>
) : StackMachine<Char, E>(instr) {
    internal var input: CharArray = charArrayOf()

    override fun consume() {
        inputOffset += 1
    }

    fun consume(n: Int): Unit {
        inputOffset += n
    }

    override fun hasMore(): Boolean {
        return input.size > inputOffset
    }

    fun hasMore(n: Int): Boolean {
        return input.size > inputOffset - n
    }

    override fun take(): Char {
        return input[inputOffset]
    }

    fun takeP(): Char = input[inputOffset]
    fun takeP(n: Int): CharArray = input.sliceArray(inputOffset until inputOffset + n)
    fun substring(start: Int): String = input.sliceArray(start until inputOffset).concatToString()
}
