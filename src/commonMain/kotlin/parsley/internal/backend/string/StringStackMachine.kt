package parsley.internal.backend.string

import parsley.internal.backend.Instruction
import parsley.internal.backend.StackMachine

internal class StringStackMachine<E>(
    instr: List<Instruction<Char, E>>
) : StackMachine<Char, E>(instr) {

    internal var input: CharArray = charArrayOf()

    override fun consume() {
        inputOffset += 1
    }
    override fun hasMore(): Boolean {
        return input.size > inputOffset
    }
    override fun take(): Char {
        return input[inputOffset]
    }
}
