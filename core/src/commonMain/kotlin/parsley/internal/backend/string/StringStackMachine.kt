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
    // TODO Optimise. Test range performance and faster ways to slice arrays in kotlin
    // The problem is that ranges cost object allocations, passing start and end doesn't
    // Solution: Use system arraycopy where possible and avoid ranges. They also box integers!
    fun substring(start: Int): String = input.sliceArray(start until inputOffset).concatToString()
}
